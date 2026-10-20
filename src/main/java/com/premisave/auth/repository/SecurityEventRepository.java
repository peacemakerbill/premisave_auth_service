package com.premisave.auth.repository;

import com.premisave.auth.entity.SecurityEvent;
import com.premisave.auth.enums.SecurityEventType;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Extends the bare Repository marker on purpose: only the methods below exist,
 * so no code path can update or delete an individual audit record.
 */
public interface SecurityEventRepository extends Repository<SecurityEvent, String> {

    <S extends SecurityEvent> S save(S event);

    List<SecurityEvent> findTop50ByUserIdOrderByCreatedAtDesc(String userId);

    long countByUserIdAndTypeAndCreatedAtAfter(String userId, SecurityEventType type, Instant after);

    /** Used only by the retention job. */
    long deleteByCreatedAtBefore(Instant cutoff);
}
