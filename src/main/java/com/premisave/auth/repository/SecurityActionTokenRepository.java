package com.premisave.auth.repository;

import com.premisave.auth.entity.SecurityActionToken;
import com.premisave.auth.enums.ActionTokenType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface SecurityActionTokenRepository extends MongoRepository<SecurityActionToken, String> {

    Optional<SecurityActionToken> findByTokenHash(String tokenHash);

    boolean existsByUserIdAndTypeAndUsedAtIsNullAndExpiresAtAfter(String userId, ActionTokenType type, Instant now);

    long deleteByExpiresAtBefore(Instant cutoff);
}
