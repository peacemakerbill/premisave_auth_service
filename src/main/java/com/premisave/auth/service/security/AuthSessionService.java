package com.premisave.auth.service.security;

import com.premisave.auth.config.SecurityProperties;
import com.premisave.auth.dto.AuthResponse;
import com.premisave.auth.entity.KnownDevice;
import com.premisave.auth.entity.SignInRecord;
import com.premisave.auth.entity.User;
import com.premisave.auth.entity.UserSession;
import com.premisave.auth.enums.LoginMethod;
import com.premisave.auth.enums.Role;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecuritySeverity;
import com.premisave.auth.repository.SignInRecordRepository;
import com.premisave.auth.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The single place where a sign-in becomes a session. Password, social, signup and refresh
 * flows all end here, so recognising the device, scoring the risk, writing history and audit,
 * deciding on an alert and issuing tokens are done the same way for every login method.
 *
 * Only the session and device records are essential. Geolocation, risk scoring, history,
 * audit and email problems are logged and skipped, so they can never stop a legitimate sign-in.
 */
@Slf4j
@Service
public class AuthSessionService {

    /** Result of a sign-in: the body for the client and, when new, the device token for the cookie. */
    public record Established(AuthResponse response, String deviceToken, UserSession session) {
    }

    private final DeviceService devices;
    private final SessionService sessions;
    private final SuspiciousActivityService suspicious;
    private final SecurityActionService actions;
    private final SecurityNotificationService notifications;
    private final SecurityAuditService audit;
    private final SignInRecordRepository history;
    private final GeoIpService geoIp;
    private final JwtService jwt;
    private final SecurityProperties properties;
    private final Clock clock;

    public AuthSessionService(DeviceService devices, SessionService sessions, SuspiciousActivityService suspicious,
                              SecurityActionService actions, SecurityNotificationService notifications,
                              SecurityAuditService audit, SignInRecordRepository history, GeoIpService geoIp,
                              JwtService jwt, SecurityProperties properties, Clock clock) {
        this.devices = devices;
        this.sessions = sessions;
        this.suspicious = suspicious;
        this.actions = actions;
        this.notifications = notifications;
        this.audit = audit;
        this.history = history;
        this.geoIp = geoIp;
        this.jwt = jwt;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param accountHasSignedInBefore whether the account has any earlier sign-in (user.lastLoginAt != null);
     *                                 a first device on a brand new account is not alerted
     * @param allowAlerts              false for silent sessions, for example a legacy token upgrade
     */
    public Established establish(User user, ClientContext ctx, LoginMethod method,
                                 boolean accountHasSignedInBefore, boolean allowAlerts) {
        Instant now = Instant.now(clock);
        DeviceInfo info = UserAgentParser.parse(ctx.userAgent());
        GeoLocation geo = lookupGeo(ctx.ip());

        DeviceService.Recognition recognition = devices.recognize(user, ctx, info, geo, accountHasSignedInBefore);
        KnownDevice device = recognition.device();

        RiskAssessment risk = assess(user, ctx, geo, recognition);
        UserSession session = sessions.open(user, device, ctx, info, geo, method);

        recordHistory(user, session, device, ctx, geo, method, recognition, risk, now);
        recordAudit(user, session, device, ctx, recognition, risk);
        alertIfNeeded(user, session, device, ctx, info, geo, method, recognition, risk, allowAlerts, now);

        String role = user.getRole() != null ? user.getRole().name() : Role.CLIENT.name();
        AuthResponse response = new AuthResponse(
                jwt.generateAccessToken(user, session.getId()),
                role,
                jwt.generateRefreshToken(user, session.getId(), session.getRefreshGeneration(), session.getExpiresAt()));
        response.setExpiresIn(jwt.accessTokenSeconds());
        if (ctx.tokenInBody() && recognition.issuedToken() != null) {
            response.setDeviceToken(recognition.issuedToken());
        }
        return new Established(response, recognition.issuedToken(), session);
    }

    private GeoLocation lookupGeo(String ip) {
        try {
            return geoIp.lookup(ip);
        } catch (RuntimeException e) {
            return GeoLocation.unavailable();
        }
    }

    private RiskAssessment assess(User user, ClientContext ctx, GeoLocation geo, DeviceService.Recognition recognition) {
        try {
            return suspicious.assess(user, ctx, geo, recognition.isNew(), recognition.firstDevice());
        } catch (RuntimeException e) {
            log.warn("Risk check skipped: {}", e.getClass().getSimpleName());
            return RiskAssessment.none();
        }
    }

    private void recordHistory(User user, UserSession session, KnownDevice device, ClientContext ctx, GeoLocation geo,
                               LoginMethod method, DeviceService.Recognition recognition, RiskAssessment risk, Instant now) {
        try {
            SignInRecord record = new SignInRecord();
            record.setUserId(user.getId());
            record.setSessionId(session.getId());
            record.setDeviceId(device.getId());
            record.setDeviceLabel(device.getLabel());
            record.setAt(now);
            record.setIpAddress(ctx.ip());
            record.setCountry(geo.country());
            record.setRegion(geo.region());
            record.setCity(geo.city());
            record.setMethod(method);
            record.setNewDevice(recognition.isNew());
            record.setRiskScore(risk.score());
            record.setRiskReasons(risk.reasons());
            history.save(record);
        } catch (RuntimeException e) {
            log.warn("Sign-in history not written: {}", e.getClass().getSimpleName());
        }
    }

    private void recordAudit(User user, UserSession session, KnownDevice device, ClientContext ctx,
                             DeviceService.Recognition recognition, RiskAssessment risk) {
        audit.event(SecurityEventType.SIGN_IN_SUCCESS)
                .user(user.getId()).actor(user.getId()).client(ctx)
                .session(session.getId()).device(device.getId())
                .detail("method", session.getLoginMethod())
                .detail("newDevice", recognition.isNew())
                .write();
        if (recognition.isNew() && !recognition.firstDevice()) {
            audit.event(SecurityEventType.NEW_DEVICE_SIGN_IN)
                    .user(user.getId()).actor(user.getId()).client(ctx)
                    .session(session.getId()).device(device.getId())
                    .detail("device", device.getLabel())
                    .write();
        }
        if (risk.suspicious()) {
            boolean admin = user.getRole() == Role.ADMIN;
            audit.event(SecurityEventType.SUSPICIOUS_SIGN_IN)
                    .severity(admin ? SecuritySeverity.CRITICAL : SecuritySeverity.WARNING)
                    .user(user.getId()).actor(user.getId()).client(ctx)
                    .session(session.getId()).device(device.getId())
                    .detail("score", risk.score())
                    .detail("reasons", String.join("; ", risk.reasons()))
                    .write();
            if (admin) {
                audit.event(SecurityEventType.ADMIN_SUSPICIOUS_ACTIVITY)
                        .severity(SecuritySeverity.CRITICAL)
                        .user(user.getId()).client(ctx).session(session.getId())
                        .reason("Suspicious sign-in on an administrator account")
                        .detail("score", risk.score())
                        .write();
            }
        }
    }

    private void alertIfNeeded(User user, UserSession session, KnownDevice device, ClientContext ctx, DeviceInfo info,
                               GeoLocation geo, LoginMethod method, DeviceService.Recognition recognition,
                               RiskAssessment risk, boolean allowAlerts, Instant now) {
        if (!allowAlerts) {
            return;
        }
        try {
            boolean trusted = properties.isDeviceTrustEnabled() && device.isTrusted();
            boolean suspiciousAlert = risk.suspicious() && properties.isSuspiciousLoginAlerts();
            boolean routineAlert = routineAlertWanted(device, recognition, trusted, now);

            if (recognition.isNew() && recognition.firstDevice() && !properties.isAlertFirstDevice()) {
                // First device of a new account: nothing to compare with. Treated as already notified.
                devices.markAlerted(device);
            }
            if (!suspiciousAlert && !routineAlert) {
                return;
            }

            String status = recognition.isNew() ? "New device" : (trusted ? "Trusted device" : "Recognized device, not trusted");
            String reportToken = actions.createReportToken(user.getId(), session.getId(), device.getId());
            notifications.sendSignInAlert(new SecurityNotificationService.SignInAlert(
                    user.getId(), user.getEmail(), user.getFirstName(), now, ctx.ip(), geo, info, status, method,
                    actions.reportUrl(reportToken), suspiciousAlert, suspiciousAlert ? risk.reasons() : List.of()));
            devices.markAlerted(device);
        } catch (RuntimeException e) {
            log.warn("Sign-in alert skipped: {}", e.getClass().getSimpleName());
        }
    }

    /** New device: alert. Trusted device: never. Recognised but untrusted: alert once per cooldown. */
    boolean routineAlertWanted(KnownDevice device, DeviceService.Recognition recognition, boolean trusted, Instant now) {
        if (!properties.isLoginEmailNotifications() || !properties.isNewDeviceAlerts()) {
            return false;
        }
        if (recognition.isNew()) {
            return !recognition.firstDevice() || properties.isAlertFirstDevice();
        }
        if (trusted) {
            return false;
        }
        if (device.getLastAlertAt() == null) {
            return true;
        }
        Duration cooldown = Duration.ofHours(Math.max(0, properties.getDeviceAlertCooldownHours()));
        return device.getLastAlertAt().plus(cooldown).isBefore(now);
    }
}
