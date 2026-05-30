package com.premisave.auth.repository;

import com.premisave.auth.entity.ProfileView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ProfileViewRepositoryImpl implements ProfileViewRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public int countUniqueViewers(String targetId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("target.$id").is(targetId)),
                Aggregation.group("$viewer.$id"),
                Aggregation.count().as("uniqueCount")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, ProfileView.class, Map.class);

        return results.getMappedResults().size();
    }
}