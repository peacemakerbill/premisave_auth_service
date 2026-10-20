package com.premisave.auth.entity;

import com.premisave.auth.enums.ActionTokenType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Single-use email token. Only the SHA-256 hash of the token is stored. */
@Data
@Document(collection = "security_action_tokens")
public class SecurityActionToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String tokenHash;

    private ActionTokenType type;
    private String userId;

    /** The sign-in being reported; null for invitations. */
    private String sessionId;
    private String deviceId;

    private Instant createdAt;
    private Instant expiresAt;
    private Instant usedAt;
}
