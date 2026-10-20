package com.premisave.auth.entity;

import com.premisave.auth.enums.LoginMethod;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One sign-in of one user on one device. Its id is the "sid" claim of the
 * access and refresh tokens, so revoking the session ends both.
 */
@Data
@Document(collection = "user_sessions")
public class UserSession {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String deviceId;
    private LoginMethod loginMethod;

    private String ipAddress;
    /** Raw User-Agent, shortened. It is an unverified hint supplied by the client. */
    private String userAgent;
    private String deviceLabel;
    private String country;
    private String region;
    private String city;

    private Instant createdAt;
    private Instant lastSeenAt;
    private Instant expiresAt;

    /** Bumped on every refresh; an older value coming back means the token was replayed. */
    private int refreshGeneration = 0;
    private Instant lastRefreshAt;

    private Instant revokedAt;
    private String revokedReason;
    private String revokedBy;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
