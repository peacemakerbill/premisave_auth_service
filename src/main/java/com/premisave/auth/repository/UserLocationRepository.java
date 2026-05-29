package com.premisave.auth.repository;

import com.premisave.auth.entity.UserLocation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserLocationRepository extends MongoRepository<UserLocation, String> {

    Optional<UserLocation> findByUserIdAndIsCurrentTrue(String userId);

    List<UserLocation> findByUserIdOrderByCreatedAtDesc(String userId);

    void deleteByUserIdAndIsCurrentTrue(String userId);

    // Find nearby users (within radius in meters) - for future use
    @Query("{'location': { $nearSphere: { $geometry: { type: 'Point', coordinates: [?0, ?1] }, $maxDistance: ?2 } }, 'isCurrent': true}")
    List<UserLocation> findNearbyUsers(Double longitude, Double latitude, Double maxDistanceInMeters);
}