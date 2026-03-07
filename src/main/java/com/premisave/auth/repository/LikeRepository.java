package com.premisave.auth.repository;

import com.premisave.auth.entity.Like;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LikeRepository extends MongoRepository<Like, String> {
}