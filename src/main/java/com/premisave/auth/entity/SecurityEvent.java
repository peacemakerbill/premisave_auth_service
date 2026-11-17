package com.premisave.auth.entity;

import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecurityOutcome;
import com.premisave.auth.enums.SecuritySeverity;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Append-only audit record. There is deliberately no API that updates or deletes
 * these rows; the only deletion is the retention job (see SecurityEventRepository).
 * Never put passwords, tokens or other secrets into details.
 */
@Data
@Document(collection = "security_events")
@CompoundIndex(name = "user_time", def = "{'userId': 1, 'createdAt': -1}")
public class SecurityEvent {

    @Id
    private String id;

    private SecurityEventType type;
    private SecuritySeverity severity;

    /** Account the event is about. Null for attempts on unknown emails. */
    private String userId;

    /** SYSTEM, ANONYMOUS or the id of the user (or administrator) who acted. */
    private String actor;

    /** For failed sign-ins on unknown accounts: a masked email and a short hash, never the raw value. */
    private String subjectMasked;
    private String subjectHash;

    private String ipAddress;
    private String sessionId;
    private String deviceId;

    private String reason;
    private Map<String, String> details;

    /** SUCCESS or FAILURE. Older records have none. */
    private SecurityOutcome outcome;

    /** Role of the person who acted, when it was a signed-in person (USER, ADMIN ...). */
    private String actorRole;

    /** What the event was done to: USER, APPLICATION, SETTINGS ... with its id and a readable label (never a secret). */
    private String targetType;
    private String targetId;
    private String targetLabel;

    /** The client that made the request: the raw User-Agent (cleaned and capped), and what could be read from it. */
    private String userAgent;
    private String deviceLabel;
    private String deviceType;
    private String browser;
    private String os;

    /** Approximate place of the IP address. Filled only when the address was already looked up (never by a new lookup). */
    private String country;
    private String region;
    private String city;

    /** The HTTP request that caused the event: method and path, without the query string. */
    private String requestMethod;
    private String requestPath;

    private Instant createdAt;
}
