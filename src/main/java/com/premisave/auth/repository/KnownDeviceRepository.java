package com.premisave.auth.repository;

import com.premisave.auth.entity.KnownDevice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KnownDeviceRepository extends MongoRepository<KnownDevice, String> {

    Optional<KnownDevice> findByUserIdAndTokenHash(String userId, String tokenHash);

    Optional<KnownDevice> findByIdAndUserId(String id, String userId);

    List<KnownDevice> findByUserIdOrderByLastSeenAtDesc(String userId);

    long countByUserId(String userId);

    List<KnownDevice> findByUserIdAndTrustedFalseOrderByLastSeenAtAsc(String userId);

    long deleteByLastSeenAtBefore(Instant cutoff);
}
