package com.premisave.auth.config;

import com.premisave.auth.entity.KnownDevice;
import com.premisave.auth.entity.SecurityActionToken;
import com.premisave.auth.entity.SecurityEvent;
import com.premisave.auth.entity.SignInRecord;
import com.premisave.auth.entity.UserSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Creates the indexes of the security collections at startup. Spring Data does not create them
 * by default, and doing it here avoids switching on auto-index-creation for every other collection.
 * Index creation is idempotent, so running it on every start is safe, and a failure is logged
 * without stopping the service.
 *
 * This is the "migration" for the new collections: MongoDB creates collections on first write,
 * so only indexes and uniqueness rules need to be applied. No existing documents are modified.
 */
@Slf4j
@Component
@Order(10)
public class SecurityIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongo;

    public SecurityIndexInitializer(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            // One record per (user, device token): prevents duplicate device registrations.
            mongo.indexOps(KnownDevice.class).createIndex(
                    new Index().on("userId", Sort.Direction.ASC).on("tokenHash", Sort.Direction.ASC)
                            .unique().named("user_token_unique"));
            mongo.indexOps(KnownDevice.class).createIndex(
                    new Index().on("userId", Sort.Direction.ASC).on("lastSeenAt", Sort.Direction.DESC).named("user_last_seen"));
            mongo.indexOps(KnownDevice.class).createIndex(
                    new Index().on("lastSeenAt", Sort.Direction.ASC).named("last_seen"));

            mongo.indexOps(UserSession.class).createIndex(
                    new Index().on("userId", Sort.Direction.ASC).on("revokedAt", Sort.Direction.ASC).named("user_revoked"));
            mongo.indexOps(UserSession.class).createIndex(
                    new Index().on("expiresAt", Sort.Direction.ASC).named("expires"));
            mongo.indexOps(UserSession.class).createIndex(
                    new Index().on("deviceId", Sort.Direction.ASC).named("device"));

            mongo.indexOps(SignInRecord.class).createIndex(
                    new Index().on("userId", Sort.Direction.ASC).on("at", Sort.Direction.DESC).named("user_time"));
            mongo.indexOps(SignInRecord.class).createIndex(
                    new Index().on("userId", Sort.Direction.ASC).on("deviceId", Sort.Direction.ASC)
                            .on("at", Sort.Direction.DESC).named("user_device_time"));
            mongo.indexOps(SignInRecord.class).createIndex(
                    new Index().on("at", Sort.Direction.ASC).named("at"));

            mongo.indexOps(SecurityEvent.class).createIndex(
                    new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("user_time"));
            mongo.indexOps(SecurityEvent.class).createIndex(
                    new Index().on("userId", Sort.Direction.ASC).on("type", Sort.Direction.ASC)
                            .on("createdAt", Sort.Direction.DESC).named("user_type_time"));
            mongo.indexOps(SecurityEvent.class).createIndex(
                    new Index().on("type", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("type_time"));
            mongo.indexOps(SecurityEvent.class).createIndex(
                    new Index().on("createdAt", Sort.Direction.ASC).named("created"));

            mongo.indexOps(SecurityActionToken.class).createIndex(
                    new Index().on("tokenHash", Sort.Direction.ASC).unique().named("token_hash_unique"));
            mongo.indexOps(SecurityActionToken.class).createIndex(
                    new Index().on("userId", Sort.Direction.ASC).on("type", Sort.Direction.ASC).named("user_type"));
            mongo.indexOps(SecurityActionToken.class).createIndex(
                    new Index().on("expiresAt", Sort.Direction.ASC).named("expires"));

            log.info("Security collection indexes are in place.");
        } catch (RuntimeException e) {
            log.error("Could not create security indexes (the service keeps running): {}", e.getClass().getSimpleName());
        }
    }
}
