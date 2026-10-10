package com.premisave.auth.service;

import com.mongodb.client.result.UpdateResult;
import com.premisave.auth.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Creates usernames for users who do not have one, and keeps doing so in the background.
 *
 * Usernames come from the person's name (john.doe) or a provider handle, never from the
 * email address, so nobody's email prefix becomes public. When the name is taken a short
 * random number is added (john.doe48). Every generated username is flagged with
 * usernameGenerated = true so the app can invite the user to pick their own; the flag is
 * cleared when they change it.
 *
 * Three entry points share the same logic:
 *   generate(...)           synchronous, used by social sign-up where the user is created on the spot
 *   assignInBackground(id)  asynchronous, started right after an email sign-up without a username
 *   backfill(...)           the sweep run by UsernameBackfillJob for anyone still without a username
 */
@Slf4j
@Service
public class UsernameService {

    static final int MIN_LENGTH = 3;
    static final int MAX_LENGTH = 30;

    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "root", "support", "system", "premisave", "staff", "moderator",
            "operations", "finance", "help", "info", "null", "undefined", "api", "www", "me", "user");

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MongoTemplate mongoTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    public UsernameService(MongoTemplate mongoTemplate, RedisTemplate<String, Object> redisTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
    }

    // ------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------

    /**
     * Returns a username that is not used by anyone yet (compared ignoring case).
     *
     * @param hint      a public handle from a sign-in provider, for example a GitHub login. May be null.
     * @param firstName may be null
     * @param lastName  may be null
     */
    public String generate(String hint, String firstName, String lastName) {
        List<String> bases = new ArrayList<>();
        addIfUsable(bases, cleanHandle(hint));

        String first = cleanName(firstName);
        String last = cleanName(lastName);
        if (!first.isEmpty() && !last.isEmpty()) {
            addIfUsable(bases, first + "." + last);
            addIfUsable(bases, first + last.charAt(0));
        } else if (!first.isEmpty()) {
            addIfUsable(bases, first);
        } else if (!last.isEmpty()) {
            addIfUsable(bases, last);
        }

        // 1. The cleanest form that is still free
        for (String base : bases) {
            if (!isTaken(base)) {
                return base;
            }
        }

        // 2. The preferred base with a short random number: john.doe48, then 3 and 4 digits if crowded
        String base = bases.isEmpty() ? "user" : bases.get(0);
        for (int attempt = 0; attempt < 30; attempt++) {
            int digits = 2 + attempt / 10;
            String candidate = truncate(base, MAX_LENGTH - digits) + randomDigits(digits);
            if (!isTaken(candidate)) {
                return candidate;
            }
        }

        // 3. Practically unreachable, but never fail a sign-up over a username
        return "user" + randomDigits(12);
    }

    /** True when any user already has this username, ignoring upper and lower case. */
    public boolean isTaken(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        Query query = new Query(Criteria.where("username")
                .regex("^" + Pattern.quote(username.trim()) + "$", "i"));
        return mongoTemplate.exists(query, User.class);
    }

    // ------------------------------------------------------------------
    // Assigning
    // ------------------------------------------------------------------

    /** Runs on a background thread. Safe to call for a user who already has a username (nothing happens). */
    @Async
    public void assignInBackground(String userId) {
        try {
            User user = mongoTemplate.findOne(
                    withNameFields(new Query(Criteria.where("_id").is(userId))), User.class);
            if (user != null) {
                assignIfMissing(user);
            }
        } catch (RuntimeException e) {
            // The scheduled sweep will pick this user up, so a failure here is not fatal
            log.warn("Background username assignment failed for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Gives the user a generated username if, and only if, they still have none. The write is
     * conditional, so two servers (or the sweep and a sign-up) can never overwrite each other
     * or replace a username the user just chose.
     *
     * @return true when a username was assigned
     */
    boolean assignIfMissing(User user) {
        String username = generate(null, user.getFirstName(), user.getLastName());

        UpdateResult result = mongoTemplate.updateFirst(
                new Query(new Criteria().andOperator(Criteria.where("_id").is(user.getId()), missingUsername())),
                new Update()
                        .set("username", username)
                        .set("usernameGenerated", true)
                        .set("updatedAt", LocalDateTime.now()),
                User.class);

        if (result.getModifiedCount() == 0) {
            return false;
        }
        // The auth filter reads users from Redis first; drop the copy that has no username
        try {
            redisTemplate.delete("user:" + user.getId());
        } catch (RuntimeException e) {
            log.warn("Could not clear cached user {}: {}", user.getId(), e.getMessage());
        }
        log.info("Generated username '{}' for user {}", username, user.getId());
        return true;
    }

    /**
     * Assigns usernames to users who have none, in batches.
     *
     * @return how many users received a username
     */
    public int backfill(int batchSize, int maxBatches) {
        int assigned = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            List<User> users = mongoTemplate.find(
                    withNameFields(new Query(missingUsername()).limit(batchSize)), User.class);
            if (users.isEmpty()) {
                break;
            }
            int assignedInBatch = 0;
            for (User user : users) {
                try {
                    if (assignIfMissing(user)) {
                        assignedInBatch++;
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not generate a username for user {}: {}", user.getId(), e.getMessage());
                }
            }
            assigned += assignedInBatch;
            if (assignedInBatch == 0) {
                break;   // nothing changed, so the next batch would return the same users
            }
        }
        return assigned;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** No username at all, or only blank spaces. */
    private static Criteria missingUsername() {
        return new Criteria().orOperator(
                Criteria.where("username").is(null),
                Criteria.where("username").regex("^\\s*$"));
    }

    /** Only load what is needed; this also avoids resolving the createdBy and updatedBy references. */
    private static Query withNameFields(Query query) {
        query.fields().include("firstName", "lastName", "email");
        return query;
    }

    private static void addIfUsable(List<String> bases, String candidate) {
        if (candidate.length() >= MIN_LENGTH && !RESERVED.contains(candidate) && !bases.contains(candidate)) {
            bases.add(truncate(candidate, MAX_LENGTH - 4));   // leave room for a numeric suffix
        }
    }

    /** Lowercase letters and digits only, accents removed (José becomes jose). */
    private static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        String plain = DIACRITICS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Same character set the profile update allows: letters, digits, dot, underscore, hyphen. */
    private static String cleanHandle(String handle) {
        if (handle == null) {
            return "";
        }
        String plain = DIACRITICS.matcher(Normalizer.normalize(handle, Normalizer.Form.NFD)).replaceAll("");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "").replaceAll("^[._-]+|[._-]+$", "");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String randomDigits(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}