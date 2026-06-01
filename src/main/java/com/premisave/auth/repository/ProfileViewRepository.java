package com.premisave.auth.repository;

import com.premisave.auth.entity.ProfileView;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProfileViewRepository extends MongoRepository<ProfileView, String>, ProfileViewRepositoryCustom {

    @Query("{'viewer': ?0, 'target': ?1, 'viewedAt': {$gt: ?2}}")
    Optional<ProfileView> findRecentView(ObjectId viewerId, ObjectId targetId, LocalDateTime after);

    @Query(value = "{'target': ?0}", sort = "{'viewedAt': -1}")
    List<ProfileView> findTop20ByTargetIdOrderByViewedAtDesc(ObjectId targetId);

    @Query(value = "{'target': ?0}", count = true)
    long countByTargetId(ObjectId targetId);

    @Query(value = "{'target': ?0, 'viewedAt': {$gte: ?1}}", count = true)
    long countViewsInLastDays(ObjectId targetId, LocalDateTime since);

    // Method for "Who I Viewed" with configurable limit
    default List<ProfileView> findRecentViewsByViewerId(ObjectId viewerId, int limit) {
        return findAllByViewerIdOrderByViewedAtDesc(viewerId, PageRequest.of(0, limit));
    }

    @Query(value = "{'viewer': ?0}", sort = "{'viewedAt': -1}")
    List<ProfileView> findAllByViewerIdOrderByViewedAtDesc(ObjectId viewerId, org.springframework.data.domain.Pageable pageable);
}