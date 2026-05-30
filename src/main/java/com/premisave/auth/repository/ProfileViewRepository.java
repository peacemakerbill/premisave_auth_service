package com.premisave.auth.repository;

import com.premisave.auth.entity.ProfileView;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProfileViewRepository extends MongoRepository<ProfileView, String> {

    Optional<ProfileView> findByViewerIdAndTargetIdAndViewedAtAfter(
            String viewerId, String targetId, LocalDateTime after);

    List<ProfileView> findByTargetIdOrderByViewedAtDesc(String targetId);

    List<ProfileView> findTop20ByTargetIdOrderByViewedAtDesc(String targetId);

    long countByTargetId(String targetId);

    @Query("{'target.$id': ?0, 'viewedAt': {$gte: ?1}}")
    long countViewsInLastDays(String targetId, LocalDateTime since);

    List<ProfileView> findByViewerIdOrderByViewedAtDesc(String viewerId);
}