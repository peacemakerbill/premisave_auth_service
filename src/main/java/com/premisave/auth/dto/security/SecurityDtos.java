package com.premisave.auth.dto.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response shapes of the /security endpoints. */
public final class SecurityDtos {

    private SecurityDtos() {
    }

    public record SessionView(String id, boolean current, String deviceId, String deviceLabel, String method,
                              String ipAddress, String country, String region, String city,
                              Instant createdAt, Instant lastSeenAt, Instant expiresAt) {
    }

    public record DeviceView(String id, boolean current, String label, String deviceType, String os, String browser,
                             Instant firstSeenAt, Instant lastSeenAt, String lastIp,
                             String country, String region, String city, int signInCount,
                             boolean trusted, Instant trustedAt, Instant trustRevokedAt, int activeSessions) {
    }

    public record SignInView(String sessionId, String deviceId, String deviceLabel, Instant at, String ipAddress,
                             String country, String region, String city, String method, boolean newDevice,
                             int riskScore, List<String> riskReasons) {
    }

    public record DeviceDetailView(DeviceView device, List<SignInView> recentSignIns) {
    }

    public record EventView(String type, String severity, Instant createdAt, String ipAddress,
                            String reason, Map<String, String> details, String outcome, String deviceLabel,
                            String browser, String os, String country, String region, String city) {
    }

    /** Admin view of an audit record. */
    public record AdminEventView(String id, String type, String severity, String outcome,
                                 String userId, String userName, String userEmail,
                                 String actor, String actorName, String actorEmail, String actorRole,
                                 String subjectMasked, String ipAddress, String country, String region, String city,
                                 String userAgent, String deviceLabel, String deviceType, String os, String browser,
                                 String sessionId, String deviceId,
                                 String targetType, String targetId, String targetLabel,
                                 String requestMethod, String requestPath,
                                 String reason, Map<String, String> details, Instant createdAt) {
    }

    /** How many events of one kind happened in the period. */
    public record EventCount(String key, long count) {
    }

    /** The audit trail at a glance for one period. */
    public record AdminSummary(Instant from, Instant to, long total, long info, long warning, long critical,
                               long failures, long signIns, long failedSignIns, long lockouts, long adminChanges,
                               long people, long addresses, List<EventCount> topTypes) {
    }

    public record PasswordConfirmation(@Size(max = 200) String currentPassword) {
    }

    public record ReportTokenRequest(@NotBlank @Size(max = 200) String token) {
    }

    public record ConfirmReportRequest(@NotBlank @Size(max = 200) String token, boolean requestPasswordReset) {
    }

    public record AcceptInvitationRequest(@NotBlank @Size(max = 200) String token,
                                          @NotBlank @Size(max = 200) String newPassword,
                                          @NotBlank @Size(max = 200) String confirmPassword) {
    }

    public record CountResult(int count, String message) {
    }
}
