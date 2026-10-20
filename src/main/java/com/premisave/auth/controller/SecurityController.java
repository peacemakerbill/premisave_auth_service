package com.premisave.auth.controller;

import com.premisave.auth.config.SecurityProperties;
import com.premisave.auth.dto.security.SecurityDtos.*;
import com.premisave.auth.entity.KnownDevice;
import com.premisave.auth.entity.SignInRecord;
import com.premisave.auth.entity.User;
import com.premisave.auth.entity.UserSession;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecuritySeverity;
import com.premisave.auth.exception.ApiException;
import com.premisave.auth.repository.SecurityEventRepository;
import com.premisave.auth.repository.SignInRecordRepository;
import com.premisave.auth.service.application.CurrentUser;
import com.premisave.auth.service.security.ClientContext;
import com.premisave.auth.service.security.ClientContextResolver;
import com.premisave.auth.service.security.DeviceService;
import com.premisave.auth.service.security.ReauthService;
import com.premisave.auth.service.security.RequestAttributes;
import com.premisave.auth.service.security.SecurityAuditService;
import com.premisave.auth.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Account security for the signed-in user: sessions, remembered devices, sign-in history.
 *
 * Every lookup is by the id AND the authenticated user's id, so someone else's session or device
 * answers 404, exactly like an id that does not exist. Ending sessions and removing devices is
 * protective and needs no extra proof; marking a device trusted is not, and asks for the password again.
 */
@RestController
@RequestMapping("/security")
public class SecurityController {

    private final CurrentUser currentUser;
    private final SessionService sessions;
    private final DeviceService devices;
    private final SignInRecordRepository signIns;
    private final SecurityEventRepository events;
    private final ReauthService reauth;
    private final SecurityAuditService audit;
    private final ClientContextResolver clientContext;
    private final SecurityProperties properties;

    public SecurityController(CurrentUser currentUser, SessionService sessions, DeviceService devices,
                              SignInRecordRepository signIns, SecurityEventRepository events, ReauthService reauth,
                              SecurityAuditService audit, ClientContextResolver clientContext,
                              SecurityProperties properties) {
        this.currentUser = currentUser;
        this.sessions = sessions;
        this.devices = devices;
        this.signIns = signIns;
        this.events = events;
        this.reauth = reauth;
        this.audit = audit;
        this.clientContext = clientContext;
        this.properties = properties;
    }

    // Sessions

    @GetMapping("/sessions")
    public List<SessionView> listSessions() {
        User user = currentUser.require();
        String current = RequestAttributes.currentSessionId();
        return sessions.activeSessions(user.getId()).stream()
                .map(s -> toView(s, s.getId().equals(current)))
                .toList();
    }

    @DeleteMapping("/sessions/{id}")
    public CountResult endSession(@PathVariable String id, HttpServletRequest http) {
        requireRevocationEnabled();
        User user = currentUser.require();
        UserSession session = sessions.findForUser(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("Session not found."));
        boolean ended = sessions.revoke(session.getId(), user.getId(), "USER_REVOKED", user.getId());
        if (ended) {
            audit.event(SecurityEventType.SESSION_REVOKED)
                    .user(user.getId()).actor(user.getId()).client(clientContext.resolve(http))
                    .session(session.getId()).device(session.getDeviceId()).reason("Ended by the account owner")
                    .write();
        }
        return new CountResult(ended ? 1 : 0, ended ? "Session ended." : "That session had already ended.");
    }

    @PostMapping("/sessions/revoke-others")
    public CountResult endOtherSessions(HttpServletRequest http) {
        requireRevocationEnabled();
        User user = currentUser.require();
        int ended = sessions.revokeAll(user.getId(), RequestAttributes.currentSessionId(), "USER_REVOKED_OTHERS", user.getId());
        audit.event(SecurityEventType.ALL_SESSIONS_REVOKED)
                .user(user.getId()).actor(user.getId()).client(clientContext.resolve(http))
                .reason("Other sessions ended by the account owner").detail("sessionsEnded", ended)
                .write();
        return new CountResult(ended, ended + " other session" + (ended == 1 ? "" : "s") + " ended.");
    }

    // Devices

    @GetMapping("/devices")
    public List<DeviceView> listDevices(HttpServletRequest http) {
        User user = currentUser.require();
        ClientContext ctx = clientContext.resolve(http);
        String currentDeviceId = devices.findByToken(user.getId(), ctx.deviceToken()).map(KnownDevice::getId).orElse(null);
        Map<String, Integer> activeByDevice = activeSessionsByDevice(user.getId());
        return devices.list(user.getId()).stream()
                .map(d -> toView(d, d.getId().equals(currentDeviceId), activeByDevice.getOrDefault(d.getId(), 0)))
                .toList();
    }

    @GetMapping("/devices/{id}")
    public DeviceDetailView deviceDetail(@PathVariable String id, HttpServletRequest http) {
        User user = currentUser.require();
        KnownDevice device = requireDevice(id, user);
        ClientContext ctx = clientContext.resolve(http);
        boolean current = devices.findByToken(user.getId(), ctx.deviceToken()).map(d -> d.getId().equals(id)).orElse(false);
        int active = activeSessionsByDevice(user.getId()).getOrDefault(id, 0);
        List<SignInView> recent = signIns.findTop10ByUserIdAndDeviceIdOrderByAtDesc(user.getId(), id).stream()
                .map(SecurityController::toView).toList();
        return new DeviceDetailView(toView(device, current, active), recent);
    }

    @PostMapping("/devices/{id}/trust")
    public DeviceView trustDevice(@PathVariable String id, @Valid @RequestBody PasswordConfirmation body,
                                  HttpServletRequest http) {
        requireTrustEnabled();
        User user = currentUser.require();
        ClientContext ctx = clientContext.resolve(http);
        KnownDevice device = requireDevice(id, user);
        reauth.require(user, RequestAttributes.currentSessionId(), body == null ? null : body.currentPassword(), ctx);
        devices.trust(device);
        audit.event(SecurityEventType.DEVICE_TRUSTED)
                .user(user.getId()).actor(user.getId()).client(ctx).device(device.getId())
                .detail("device", device.getLabel())
                .write();
        return toView(device, false, activeSessionsByDevice(user.getId()).getOrDefault(id, 0));
    }

    @DeleteMapping("/devices/{id}/trust")
    public DeviceView revokeTrust(@PathVariable String id, HttpServletRequest http) {
        requireTrustEnabled();
        User user = currentUser.require();
        KnownDevice device = requireDevice(id, user);
        devices.revokeTrust(device);
        audit.event(SecurityEventType.DEVICE_TRUST_REVOKED)
                .user(user.getId()).actor(user.getId()).client(clientContext.resolve(http)).device(device.getId())
                .write();
        return toView(device, false, activeSessionsByDevice(user.getId()).getOrDefault(id, 0));
    }

    /** The remembered-device cookie or token stops matching: that browser is a new device on its next sign-in. */
    @PostMapping("/devices/{id}/revoke-credential")
    public DeviceView revokeCredential(@PathVariable String id, HttpServletRequest http) {
        User user = currentUser.require();
        KnownDevice device = requireDevice(id, user);
        devices.revokeCredential(device);
        audit.event(SecurityEventType.DEVICE_CREDENTIAL_REVOKED)
                .user(user.getId()).actor(user.getId()).client(clientContext.resolve(http)).device(device.getId())
                .write();
        return toView(device, false, activeSessionsByDevice(user.getId()).getOrDefault(id, 0));
    }

    /** Forget a device: its sessions end, its remembered credential and trust are gone. */
    @DeleteMapping("/devices/{id}")
    public CountResult removeDevice(@PathVariable String id, HttpServletRequest http) {
        requireRevocationEnabled();
        User user = currentUser.require();
        KnownDevice device = requireDevice(id, user);
        int ended = sessions.revokeForDevice(user.getId(), device.getId(), "DEVICE_REMOVED", user.getId());
        devices.remove(device);
        audit.event(SecurityEventType.DEVICE_REMOVED)
                .user(user.getId()).actor(user.getId()).client(clientContext.resolve(http)).device(device.getId())
                .detail("sessionsEnded", ended).detail("device", device.getLabel())
                .write();
        return new CountResult(ended, "Device removed and " + ended + " session" + (ended == 1 ? "" : "s") + " ended.");
    }

    // History

    @GetMapping("/sign-ins")
    public List<SignInView> signInHistory() {
        User user = currentUser.require();
        return signIns.findTop30ByUserIdOrderByAtDesc(user.getId()).stream().map(SecurityController::toView).toList();
    }

    @GetMapping("/events")
    public List<EventView> myEvents() {
        User user = currentUser.require();
        return events.findTop50ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(e -> new EventView(e.getType().name(), e.getSeverity() == null ? SecuritySeverity.INFO.name() : e.getSeverity().name(),
                        e.getCreatedAt(), e.getIpAddress(), e.getReason(), e.getDetails()))
                .toList();
    }

    // Helpers

    private KnownDevice requireDevice(String id, User user) {
        return devices.find(id, user.getId()).orElseThrow(() -> ApiException.notFound("Device not found."));
    }

    private Map<String, Integer> activeSessionsByDevice(String userId) {
        Map<String, Integer> counts = new HashMap<>();
        for (UserSession s : sessions.activeSessions(userId)) {
            if (s.getDeviceId() != null) {
                counts.merge(s.getDeviceId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private void requireRevocationEnabled() {
        if (!properties.isSessionRevocationEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Session management is turned off on this server.");
        }
    }

    private void requireTrustEnabled() {
        if (!properties.isDeviceTrustEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Trusted devices are turned off on this server.");
        }
    }

    static SessionView toView(UserSession s, boolean current) {
        return new SessionView(s.getId(), current, s.getDeviceId(), s.getDeviceLabel(),
                s.getLoginMethod() == null ? null : s.getLoginMethod().name(), s.getIpAddress(),
                s.getCountry(), s.getRegion(), s.getCity(), s.getCreatedAt(), s.getLastSeenAt(), s.getExpiresAt());
    }

    static DeviceView toView(KnownDevice d, boolean current, int activeSessions) {
        return new DeviceView(d.getId(), current, d.getLabel(), d.getDeviceType(), d.getOs(), d.getBrowser(),
                d.getFirstSeenAt(), d.getLastSeenAt(), d.getLastIp(), d.getLastCountry(), d.getLastRegion(),
                d.getLastCity(), d.getSignInCount(), d.isTrusted(), d.getTrustedAt(), d.getTrustRevokedAt(), activeSessions);
    }

    static SignInView toView(SignInRecord r) {
        return new SignInView(r.getSessionId(), r.getDeviceId(), r.getDeviceLabel(), r.getAt(), r.getIpAddress(),
                r.getCountry(), r.getRegion(), r.getCity(), r.getMethod() == null ? null : r.getMethod().name(),
                r.isNewDevice(), r.getRiskScore(), r.getRiskReasons());
    }
}
