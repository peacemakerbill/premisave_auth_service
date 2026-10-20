package com.premisave.auth.service.security;

import com.premisave.auth.config.SecurityProperties;
import com.premisave.auth.repository.KnownDeviceRepository;
import com.premisave.auth.repository.SecurityActionTokenRepository;
import com.premisave.auth.repository.SecurityEventRepository;
import com.premisave.auth.repository.SignInRecordRepository;
import com.premisave.auth.repository.UserSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Applies the retention limits once a day: security events and sign-in history older than
 * app.security.audit-retention-days, sessions that ended longer ago than that, devices not seen for
 * app.security.device-retention-days, and email action tokens a week after they expired.
 * Needs scheduling to be enabled (SchedulingConfig with @EnableScheduling).
 */
@Slf4j
@Component
public class SecurityRetentionJob {

    private final SecurityEventRepository events;
    private final SignInRecordRepository signIns;
    private final UserSessionRepository sessions;
    private final KnownDeviceRepository devices;
    private final SecurityActionTokenRepository tokens;
    private final SecurityProperties properties;
    private final Clock clock;

    public SecurityRetentionJob(SecurityEventRepository events, SignInRecordRepository signIns,
                                UserSessionRepository sessions, KnownDeviceRepository devices,
                                SecurityActionTokenRepository tokens, SecurityProperties properties, Clock clock) {
        this.events = events;
        this.signIns = signIns;
        this.sessions = sessions;
        this.devices = devices;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.security.retention-cron:0 15 3 * * *}")
    public void purge() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            log.error("Security retention run failed: {}", e.getClass().getSimpleName());
        }
    }

    void runOnce() {
        Instant now = Instant.now(clock);
        Instant auditCutoff = now.minus(Duration.ofDays(Math.max(1, properties.getAuditRetentionDays())));
        Instant deviceCutoff = now.minus(Duration.ofDays(Math.max(1, properties.getDeviceRetentionDays())));

        long removedEvents = events.deleteByCreatedAtBefore(auditCutoff);
        long removedSignIns = signIns.deleteByAtBefore(auditCutoff);
        long removedSessions = sessions.deleteByExpiresAtBefore(auditCutoff);
        long removedDevices = devices.deleteByLastSeenAtBefore(deviceCutoff);
        long removedTokens = tokens.deleteByExpiresAtBefore(now.minus(Duration.ofDays(7)));

        log.info("Security retention: {} events, {} sign-ins, {} sessions, {} devices, {} action tokens removed",
                removedEvents, removedSignIns, removedSessions, removedDevices, removedTokens);
    }
}
