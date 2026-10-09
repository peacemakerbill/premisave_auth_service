package com.premisave.auth.service.application;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Year;

/** Produces references like HOA-2026-000123, using an atomic counter per year. */
@Component
public class ApplicationNumberGenerator {

    private static final String COLLECTION = "counters";

    private final MongoTemplate mongoTemplate;

    public ApplicationNumberGenerator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String next() {
        int year = Year.now().getValue();
        Query query = Query.query(Criteria.where("_id").is("home_owner_application_" + year));
        Update update = new Update().inc("seq", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);

        Document counter = mongoTemplate.findAndModify(query, update, options, Document.class, COLLECTION);
        Number seq = counter == null ? null : counter.get("seq", Number.class);
        long value = seq == null ? 1L : seq.longValue();
        return String.format("HOA-%d-%06d", year, value);
    }
}