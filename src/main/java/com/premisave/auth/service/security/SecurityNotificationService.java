package com.premisave.auth.service.security;

import com.premisave.auth.config.SecurityProperties;
import com.premisave.auth.enums.LoginMethod;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecuritySeverity;
import com.premisave.auth.service.EmailService;
import com.premisave.auth.service.security.EmailTemplateRenderer.Row;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sends the security emails, always asynchronously and always with an HTML and a plain-text
 * body. A delivery failure is recorded as a NOTIFICATION_FAILED audit event (class name only,
 * no address, no message text) and never reaches the caller, so authentication and
 * provisioning state are unaffected by a mail outage.
 */
@Slf4j
@Service
public class SecurityNotificationService {

    /** Everything the sign-in alert shows. */
    public record SignInAlert(String userId, String email, String firstName, Instant at, String ip,
                              GeoLocation geo, DeviceInfo device, String deviceStatus, LoginMethod method,
                              String reportUrl, boolean suspicious, List<String> reasons) {
    }

    private final EmailService emailService;
    private final EmailTemplateRenderer renderer;
    private final SecurityAuditService audit;
    private final SecurityProperties properties;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public SecurityNotificationService(EmailService emailService, EmailTemplateRenderer renderer,
                                       SecurityAuditService audit, SecurityProperties properties) {
        this.emailService = emailService;
        this.renderer = renderer;
        this.audit = audit;
        this.properties = properties;
    }

    public String securityUrl() {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + properties.getSecurityPagePath();
    }

    @Async
    public void sendSignInAlert(SignInAlert a) {
        try {
            composeSignInAlert(a);
        } catch (RuntimeException e) {
            recordFailure(a == null ? null : a.userId(), a != null && a.suspicious() ? "suspicious-sign-in" : "new-sign-in", e);
        }
    }

    private void composeSignInAlert(SignInAlert a) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Time", formatTime(a.at())));
        rows.add(new Row("Account", a.email()));
        rows.add(new Row("Device", a.device().label()));
        rows.add(new Row("Operating system", a.device().os()));
        rows.add(new Row("Browser or app", a.device().browser()));
        rows.add(new Row("Device type", a.device().deviceType() == null ? null : a.device().deviceType().toLowerCase(Locale.ROOT)));
        rows.add(new Row("IP address", a.ip()));
        rows.add(new Row("Approximate location", a.geo().describe()));
        rows.add(new Row("Sign-in method", a.method().label()));
        rows.add(new Row("Device status", a.deviceStatus()));
        if (a.suspicious() && a.reasons() != null) {
            rows.add(new Row("Why we flagged it", String.join("; ", a.reasons())));
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("name", greeting(a.firstName()));
        vars.put("email", a.email());
        vars.put("reportUrl", a.reportUrl());
        vars.put("reportHours", String.valueOf(Math.max(1, properties.getActionTokenTtlMinutes() / 60)));
        vars.put("securityUrl", securityUrl());

        String template = a.suspicious() ? "suspicious-sign-in" : "new-sign-in";
        vars.put("preheader", a.suspicious() ? "A sign-in to your account looked unusual." : "A new sign-in to your account was detected.");
        deliver(a.userId(), a.email(), template,
                a.suspicious() ? "Unusual sign-in to your Premisave account" : "New sign-in to your Premisave account",
                vars, rows);
    }

    @Async
    public void sendAdminProvisioned(String userId, String email, String activationUrl, Instant expiresAt) {
        List<Row> rows = List.of(
                new Row("Account", email),
                new Row("Role", "Administrator"),
                new Row("Link valid until", formatTime(expiresAt)));
        Map<String, String> vars = new HashMap<>();
        vars.put("email", email);
        vars.put("activationUrl", activationUrl);
        vars.put("expires", formatTime(expiresAt));
        vars.put("preheader", "Choose your password to activate your administrator account.");
        deliver(userId, email, "admin-provisioned", "Activate your Premisave administrator account", vars, rows);
    }

    @Async
    public void sendAdminRoleAssigned(String userId, String email, String firstName, String reason, Instant at) {
        List<Row> rows = List.of(
                new Row("Account", email),
                new Row("New role", "ADMIN"),
                new Row("When", formatTime(at)),
                new Row("Reason", reason));
        Map<String, String> vars = new HashMap<>();
        vars.put("name", greeting(firstName));
        vars.put("email", email);
        vars.put("securityUrl", securityUrl());
        vars.put("preheader", "Your account now has administrator access.");
        deliver(userId, email, "admin-role-assigned", "Administrator access granted on your Premisave account", vars, rows);
    }

    /** Generic notice used for password changes, session revocation, lockouts, token replay and the like. */
    @Async
    public void sendNotice(String userId, String email, String firstName, String subject, String title, String intro,
                           List<Row> rows, String warning, String buttonLabel, String buttonUrl) {
        Map<String, String> vars = new HashMap<>();
        vars.put("name", greeting(firstName));
        vars.put("title", title);
        vars.put("intro", intro);
        vars.put("warning", warning);
        vars.put("buttonLabel", buttonLabel);
        vars.put("buttonUrl", buttonUrl);
        vars.put("preheader", intro);
        deliver(userId, email, "security-notice", subject, vars, rows == null ? List.of() : rows);
    }

    @Async
    public void sendPasswordChanged(String userId, String email, String firstName, String ip, int sessionsEnded, String how) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Time", formatTime(Instant.now())));
        rows.add(new Row("How", how));
        rows.add(new Row("IP address", ip));
        rows.add(new Row("Other sessions signed out", String.valueOf(sessionsEnded)));
        Map<String, String> vars = new HashMap<>();
        vars.put("name", greeting(firstName));
        vars.put("title", "Your password was changed");
        vars.put("intro", "The password for your Premisave account " + email + " was changed.");
        vars.put("warning", "If you did not do this, reset your password immediately and contact support.");
        vars.put("buttonLabel", "Review account security");
        vars.put("buttonUrl", securityUrl());
        vars.put("preheader", "Your password was changed.");
        deliver(userId, email, "security-notice", "Your Premisave password was changed", vars, rows);
    }

    @Async
    public void sendAccountLocked(String userId, String email, String firstName, String ip, int minutes) {
        List<Row> rows = List.of(
                new Row("Time", formatTime(Instant.now())),
                new Row("Latest attempt from", ip),
                new Row("Sign-in paused for", minutes + " minutes"));
        Map<String, String> vars = new HashMap<>();
        vars.put("name", greeting(firstName));
        vars.put("title", "Sign-in temporarily paused");
        vars.put("intro", "There were several unsuccessful attempts to sign in to your account " + email
                + ". As a precaution, sign-in is paused for a short time. It resumes automatically.");
        vars.put("warning", "If this was not you, someone may be guessing your password. Consider resetting it.");
        vars.put("buttonLabel", "Review account security");
        vars.put("buttonUrl", securityUrl());
        vars.put("preheader", "Sign-in to your account is paused for a few minutes.");
        deliver(userId, email, "security-notice", "Sign-in to your Premisave account was paused", vars, rows);
    }

    private void deliver(String userId, String to, String template, String subject, Map<String, String> vars, List<Row> rows) {
        try {
            vars.putIfAbsent("subject", subject);
            EmailTemplateRenderer.Rendered rendered = renderer.render(template, vars, rows);
            emailService.sendHtml(to, subject, rendered.html());
        } catch (Exception e) {
            recordFailure(userId, template, e);
        }
    }

    /** Class name only: the exception text can contain the address or provider details. */
    private void recordFailure(String userId, String template, Exception e) {
        log.warn("Security email '{}' could not be sent ({})", template, e.getClass().getSimpleName());
        audit.event(SecurityEventType.NOTIFICATION_FAILED)
                .severity(SecuritySeverity.WARNING)
                .user(userId)
                .detail("template", template)
                .detail("error", e.getClass().getSimpleName())
                .write();
    }

    private String formatTime(Instant instant) {
        ZoneId zone;
        try {
            zone = ZoneId.of(properties.getDisplayZone());
        } catch (RuntimeException e) {
            zone = ZoneId.of("UTC");
        }
        return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z", Locale.ENGLISH).withZone(zone).format(instant);
    }

    private static String greeting(String firstName) {
        return firstName == null || firstName.isBlank() ? "there" : firstName.trim();
    }
}