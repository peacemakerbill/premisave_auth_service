package com.premisave.auth.repository;

import com.premisave.auth.entity.Follower;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FollowerRepository extends MongoRepository<Follower, String> {
}