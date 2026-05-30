package com.premisave.auth.repository;

import com.premisave.auth.entity.ProfileView;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProfileViewRepository extends MongoRepository<ProfileView, String>, ProfileViewRepositoryCustom {

    @Query("{'viewer.$id': ?0, 'target.$id': ?1, 'viewedAt': {$gt: ?2}}")
    Optional<ProfileView> findRecentView(String viewerId, String targetId, LocalDateTime after);

    @Query(value = "{'target.$id': ?0}", sort = "{'viewedAt': -1}")
    List<ProfileView> findTop20ByTargetIdOrderByViewedAtDesc(String targetId);

    @Query(value = "{'target.$id': ?0}", count = true)
    long countByTargetId(String targetId);

    @Query(value = "{'target.$id': ?0, 'viewedAt': {$gte: ?1}}", count = true)
    long countViewsInLastDays(String targetId, LocalDateTime since);

    @Query(value = "{'viewer.$id': ?0}", sort = "{'viewedAt': -1}")
    List<ProfileView> findByViewerIdOrderByViewedAtDesc(String viewerId);
}