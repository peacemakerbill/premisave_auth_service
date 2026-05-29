package com.premisave.auth.repository;

import com.premisave.auth.entity.Follower;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FollowerRepository extends MongoRepository<Follower, String> {

    Optional<Follower> findByUserIdAndFollowerId(String userId, String followerId);

    void deleteByUserIdAndFollowerId(String userId, String followerId);

    long countByUserId(String userId);      // Followers count
    long countByFollowerId(String userId);  // Following count
}