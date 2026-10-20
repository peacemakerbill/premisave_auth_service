package com.premisave.auth.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A browser or app that has signed in to an account. It is recognised by an opaque
 * random token that is kept in an HttpOnly cookie (or a header for apps); only the
 * SHA-256 hash of that token is stored. This is a recognition hint, not proof of identity.
 */
@Data
@Document(collection = "known_devices")
@CompoundIndex(name = "user_token_unique", def = "{'userId': 1, 'tokenHash': 1}", unique = true)
public class KnownDevice {

    @Id
    private String id;

    private String userId;
    private String tokenHash;

    private String label;
    private String deviceType;
    private String os;
    private String browser;

    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private String lastIp;
    private String lastCountry;
    private String lastRegion;
    private String lastCity;
    private int signInCount;

    private boolean trusted;
    private Instant trustedAt;
    private Instant trustRevokedAt;

    /** When the owner was last emailed about this device; drives the alert cooldown. */
    private Instant lastAlertAt;
}
