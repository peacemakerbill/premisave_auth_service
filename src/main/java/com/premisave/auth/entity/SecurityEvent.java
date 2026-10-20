package com.premisave.auth.entity;

import com.premisave.auth.enums.SecurityEventType;
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

    private Instant createdAt;
}
