package com.premisave.auth.repository;

import com.premisave.auth.entity.Like;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LikeRepository extends MongoRepository<Like, String> {

    Optional<Like> findByUserIdAndTargetId(String userId, String targetId);

    void deleteByUserIdAndTargetId(String userId, String targetId);

    long countByTargetId(String targetId);
    long countByUserId(String userId);

    List<Like> findByUserId(String userId);

    // Who liked me — inbound
    List<Like> findByTargetId(String targetId);
}