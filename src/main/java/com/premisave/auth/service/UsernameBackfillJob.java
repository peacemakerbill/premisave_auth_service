package com.premisave.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background sweep that gives a generated username to every user who has none: legacy
 * accounts, accounts created by an administrator, and anyone the sign-up time assignment missed.
 *
 * Settings (all optional):
 *   username-generation.enabled           true
 *   username-generation.interval-ms       300000   pause between runs (5 minutes)
 *   username-generation.initial-delay-ms  30000    wait after start-up before the first run
 *   username-generation.batch-size        200
 *   username-generation.max-batches       25       per run, so one run stays short
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "username-generation.enabled", havingValue = "true", matchIfMissing = true)
public class UsernameBackfillJob {

    private final UsernameService usernameService;
    private final int batchSize;
    private final int maxBatches;

    public UsernameBackfillJob(UsernameService usernameService,
                               @Value("${username-generation.batch-size:200}") int batchSize,
                               @Value("${username-generation.max-batches:25}") int maxBatches) {
        this.usernameService = usernameService;
        this.batchSize = Math.max(1, batchSize);
        this.maxBatches = Math.max(1, maxBatches);
    }

    @Scheduled(
            initialDelayString = "${username-generation.initial-delay-ms:30000}",
            fixedDelayString = "${username-generation.interval-ms:300000}")
    public void run() {
        try {
            int assigned = usernameService.backfill(batchSize, maxBatches);
            if (assigned > 0) {
                log.info("Username sweep: generated {} username(s)", assigned);
            }
        } catch (RuntimeException e) {
            log.error("Username sweep failed, it will retry on the next run: {}", e.getMessage());
        }
    }
}