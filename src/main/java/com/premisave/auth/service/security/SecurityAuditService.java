package com.premisave.auth.service.security;

import com.premisave.auth.entity.SecurityEvent;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecuritySeverity;
import com.premisave.auth.repository.SecurityEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Writes the audit trail. A failure to write is logged and swallowed: auditing must never
 * be the reason a sign-in or an administrator action fails.
 */
@Slf4j
@Service
public class SecurityAuditService {

    private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z0-9_.-]{1,40}$");
    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(pass|token|secret|cookie|authorization|code).*");
    private static final int MAX_VALUE = 200;

    private final SecurityEventRepository repository;
    private final Clock clock;

    public SecurityAuditService(SecurityEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Builder event(SecurityEventType type) {
        return new Builder(type);
    }

    public final class Builder {
        private final SecurityEvent event = new SecurityEvent();
        private final Map<String, String> details = new LinkedHashMap<>();

        private Builder(SecurityEventType type) {
            event.setType(type);
            event.setSeverity(SecuritySeverity.INFO);
            event.setActor("SYSTEM");
        }

        public Builder severity(SecuritySeverity severity) {
            event.setSeverity(severity);
            return this;
        }

        public Builder user(String userId) {
            event.setUserId(userId);
            return this;
        }

        public Builder actor(String actor) {
            event.setActor(actor);
            return this;
        }

        public Builder subject(String maskedEmail, String hash) {
            event.setSubjectMasked(maskedEmail);
            event.setSubjectHash(hash);
            return this;
        }

        public Builder client(ClientContext ctx) {
            if (ctx != null) {
                event.setIpAddress(ctx.ip());
            }
            return this;
        }

        public Builder session(String sessionId) {
            event.setSessionId(sessionId);
            return this;
        }

        public Builder device(String deviceId) {
            event.setDeviceId(deviceId);
            return this;
        }

        public Builder reason(String reason) {
            event.setReason(truncate(reason));
            return this;
        }

        public Builder detail(String key, Object value) {
            if (key != null && value != null && SAFE_KEY.matcher(key).matches() && !SECRET_KEY.matcher(key).matches()) {
                details.put(key, truncate(String.valueOf(value)));
            }
            return this;
        }

        /** Persists the event. Never throws. */
        public void write() {
            try {
                event.setDetails(details.isEmpty() ? null : details);
                event.setCreatedAt(Instant.now(clock));
                repository.save(event);
            } catch (RuntimeException e) {
                log.error("Could not write security event {}: {}", event.getType(), e.getClass().getSimpleName());
            }
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return cleaned.length() > MAX_VALUE ? cleaned.substring(0, MAX_VALUE) : cleaned;
    }
}
