package com.premisave.auth.repository;

import com.premisave.auth.entity.Review;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends MongoRepository<Review, String> {

    Optional<Review> findByUserIdAndTargetId(String userId, String targetId);

    List<Review> findByTargetId(String targetId);

    // Reviews I wrote — outbound
    List<Review> findByUserId(String userId);

    long countByTargetId(String targetId);

    @Aggregation(pipeline = {
        "{ $match: { targetId: ?0 } }",
        "{ $group: { _id: null, avgRating: { $avg: '$rating' } } }"
    })
    Double getAverageRatingByTargetId(String targetId);
}