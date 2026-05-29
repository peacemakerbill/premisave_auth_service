package com.premisave.auth.repository;

import com.premisave.auth.entity.UserLocation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserLocationRepository extends MongoRepository<UserLocation, String> {

    Optional<UserLocation> findByUserIdAndIsCurrentTrue(String userId);

    List<UserLocation> findByUserIdOrderByCreatedAtDesc(String userId);

    // Delete all current flags for a user (safety method)
    void deleteByUserIdAndIsCurrentTrue(String userId);

    // Optional: Find locations within a time range
    List<UserLocation> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String userId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    // Nearby users query (for future social features)
    @Query("{'location': { $nearSphere: { $geometry: { type: 'Point', coordinates: [?0, ?1] }, $maxDistance: ?2 } }, 'isCurrent': true}")
    List<UserLocation> findNearbyUsers(Double longitude, Double latitude, Double maxDistanceInMeters);
}