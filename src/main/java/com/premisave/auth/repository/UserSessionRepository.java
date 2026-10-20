package com.premisave.auth.repository;

import com.premisave.auth.entity.UserSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends MongoRepository<UserSession, String> {

    Optional<UserSession> findByIdAndUserId(String id, String userId);

    List<UserSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(String userId, Instant now);

    long deleteByExpiresAtBefore(Instant cutoff);
}
