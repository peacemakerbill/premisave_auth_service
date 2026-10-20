package com.premisave.auth.repository;

import com.premisave.auth.entity.SignInRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface SignInRecordRepository extends MongoRepository<SignInRecord, String> {

    List<SignInRecord> findTop30ByUserIdOrderByAtDesc(String userId);

    List<SignInRecord> findTop10ByUserIdAndDeviceIdOrderByAtDesc(String userId, String deviceId);

    List<SignInRecord> findByUserIdAndAtAfterOrderByAtDesc(String userId, Instant after);

    long deleteByAtBefore(Instant cutoff);
}
