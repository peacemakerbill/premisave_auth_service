package com.premisave.auth.service.security;

import com.premisave.auth.entity.SecurityEvent;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.SecurityOutcome;
import com.premisave.auth.enums.SecuritySeverity;
import com.premisave.auth.repository.SecurityEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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

    /** Event types that describe something that went wrong. Everything else is recorded as a success unless the caller says otherwise. */
    private static final Set<SecurityEventType> FAILURES = EnumSet.of(
            SecurityEventType.SIGN_IN_FAILED, SecurityEventType.SIGN_IN_THROTTLED, SecurityEventType.ACCOUNT_LOCKED,
            SecurityEventType.REFRESH_TOKEN_REUSE, SecurityEventType.CONTACT_MESSAGE_FAILED,
            SecurityEventType.NOTIFICATION_FAILED);

    private final SecurityEventRepository repository;
    private final Clock clock;
    private final GeoIpService geoIp;

    public SecurityAuditService(SecurityEventRepository repository, Clock clock, GeoIpService geoIp) {
        this.repository = repository;
        this.clock = clock;
        this.geoIp = geoIp;
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

        /**
         * The client of the request: its address, and what can be read from its User-Agent (device, browser, system). The place of
         * the address is added only when it is already known, so writing an event never waits for a location lookup.
         */
        public Builder client(ClientContext ctx) {
            if (ctx != null) {
                event.setIpAddress(ctx.ip());
                String ua = UserAgentParser.sanitize(ctx.userAgent());
                if (!ua.isEmpty()) {
                    event.setUserAgent(ua);
                    DeviceInfo info = UserAgentParser.parse(ua);
                    event.setDeviceLabel(info.label());
                    event.setDeviceType(info.deviceType());
                    event.setOs(info.os());
                    event.setBrowser(info.browser());
                }
                if (ctx.ip() != null) {
                    GeoLocation geo = geoIp.cached(ctx.ip());
                    event.setCountry(geo.country());
                    event.setRegion(geo.region());
                    event.setCity(geo.city());
                }
            }
            return this;
        }

        public Builder outcome(SecurityOutcome outcome) {
            event.setOutcome(outcome);
            return this;
        }

        /** What the event was done to, for example ("USER", id, "Jane Doe (jane@x.com)"). The label must not hold a secret. */
        public Builder target(String type, String id, String label) {
            event.setTargetType(type);
            event.setTargetId(id);
            event.setTargetLabel(truncate(label));
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
                completeFromRequest();
                if (event.getOutcome() == null) {
                    event.setOutcome(FAILURES.contains(event.getType()) ? SecurityOutcome.FAILURE : SecurityOutcome.SUCCESS);
                }
                repository.save(event);
            } catch (RuntimeException e) {
                log.error("Could not write security event {}: {}", event.getType(), e.getClass().getSimpleName());
            }
        }

        /** Fills what the caller left out from the request being handled: client, session, method, path and the actor's role. */
        private void completeFromRequest() {
            if (event.getIpAddress() == null && event.getUserAgent() == null) {
                client(RequestAttributes.currentClient());
            }
            if (event.getSessionId() == null) {
                event.setSessionId(RequestAttributes.currentSessionId());
            }
            event.setRequestMethod(RequestAttributes.currentMethod());
            event.setRequestPath(RequestAttributes.currentPath());
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User signedIn && signedIn.getId() != null
                    && signedIn.getId().equals(event.getActor()) && signedIn.getRole() != null) {
                event.setActorRole(signedIn.getRole().name());
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
