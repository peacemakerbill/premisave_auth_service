package com.premisave.auth.controller;

import com.premisave.auth.dto.security.SecurityDtos.AdminEventView;
import com.premisave.auth.dto.security.SecurityDtos.AdminSummary;
import com.premisave.auth.dto.security.SecurityDtos.EventCount;
import com.premisave.auth.entity.SecurityEvent;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecurityOutcome;
import com.premisave.auth.enums.SecuritySeverity;
import com.premisave.auth.exception.ApiException;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Read-only view of the audit trail for administrators (/admin/** requires the ADMIN role): a filtered list, one full record,
 * and a summary of a period. There is no endpoint that edits or deletes events.
 *
 * Names and emails are looked up when a record is read, so the trail never copies personal details that the account already holds.
 */
@RestController
@RequestMapping("/admin/security")
public class AdminSecurityController {

    private static final Set<String> NOT_AN_ACCOUNT = Set.of("SYSTEM", "ANONYMOUS", "unknown");
    private static final Pattern IP = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private static final Set<SecurityEventType> ADMIN_CHANGES = EnumSet.of(
            SecurityEventType.ADMIN_ROLE_CHANGED, SecurityEventType.ADMIN_ROLE_ASSIGNED, SecurityEventType.USER_CREATED,
            SecurityEventType.USER_UPDATED, SecurityEventType.USER_DELETED, SecurityEventType.USER_ARCHIVED,
            SecurityEventType.USER_UNARCHIVED, SecurityEventType.USER_ACTIVATED, SecurityEventType.USER_DEACTIVATED,
            SecurityEventType.USER_VERIFIED_BY_ADMIN, SecurityEventType.USER_UNVERIFIED_BY_ADMIN,
            SecurityEventType.STORAGE_SETTINGS_CHANGED, SecurityEventType.HOME_OWNER_PROMOTED,
            SecurityEventType.APPLICATION_STATUS_CHANGED);

    private final MongoTemplate mongo;

    public AdminSecurityController(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * The trail, newest first. Every filter is optional: q (free text, see {@link #search}), userId (the account), actor (who
     * did it), type, severity, outcome, ip, and from / to (an ISO date such as 2026-11-18 or an instant).
     */
    @GetMapping("/events")
    public List<AdminEventView> events(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String userId,
                                       @RequestParam(required = false) String actor,
                                       @RequestParam(required = false) SecurityEventType type,
                                       @RequestParam(required = false) SecuritySeverity severity,
                                       @RequestParam(required = false) SecurityOutcome outcome,
                                       @RequestParam(required = false) String ip,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Query query = new Query();
        Criteria text = search(q);
        if (text != null) {
            query.addCriteria(text);
        }
        if (userId != null && !userId.isBlank()) {
            query.addCriteria(Criteria.where("userId").is(userId));
        }
        if (actor != null && !actor.isBlank()) {
            query.addCriteria(Criteria.where("actor").is(actor));
        }
        if (type != null) {
            query.addCriteria(Criteria.where("type").is(type.name()));
        }
        if (severity != null) {
            query.addCriteria(Criteria.where("severity").is(severity.name()));
        }
        if (outcome != null) {
            query.addCriteria(Criteria.where("outcome").is(outcome.name()));
        }
        if (ip != null && !ip.isBlank()) {
            if (!IP.matcher(ip.trim()).matches()) {
                throw ApiException.badRequest("Enter a valid IP address.");
            }
            query.addCriteria(Criteria.where("ipAddress").is(ip.trim()));
        }
        Instant start = parse(from, false);
        Instant end = parse(to, true);
        if (start != null || end != null) {
            Criteria range = Criteria.where("createdAt");
            if (start != null) {
                range = range.gte(start);
            }
            if (end != null) {
                range = range.lt(end);
            }
            query.addCriteria(range);
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) Math.max(page, 0) * safeSize)
                .limit(safeSize);
        return views(mongo.find(query, SecurityEvent.class));
    }

    /** One record with everything saved about it. */
    @GetMapping("/events/{id}")
    public AdminEventView event(@PathVariable String id) {
        SecurityEvent event = mongo.findById(id, SecurityEvent.class);
        if (event == null) {
            throw ApiException.notFound("Event not found.");
        }
        return views(List.of(event)).get(0);
    }

    /** Counts for the last {@code hours} hours (1 to 720): by severity, sign-ins, failures, lockouts, administrator changes and the most common events. */
    @GetMapping("/summary")
    public AdminSummary summary(@RequestParam(defaultValue = "24") int hours) {
        int safeHours = Math.min(Math.max(hours, 1), 720);
        Instant to = Instant.now();
        Instant from = to.minus(safeHours, ChronoUnit.HOURS);
        Criteria inPeriod = Criteria.where("createdAt").gte(from).lt(to);

        long total = count(inPeriod);
        long info = count(inPeriod, Criteria.where("severity").is(SecuritySeverity.INFO.name()));
        long warning = count(inPeriod, Criteria.where("severity").is(SecuritySeverity.WARNING.name()));
        long critical = count(inPeriod, Criteria.where("severity").is(SecuritySeverity.CRITICAL.name()));
        long failures = count(inPeriod, Criteria.where("outcome").is(SecurityOutcome.FAILURE.name()));
        long signIns = count(inPeriod, Criteria.where("type").is(SecurityEventType.SIGN_IN_SUCCESS.name()));
        long failed = count(inPeriod, Criteria.where("type").is(SecurityEventType.SIGN_IN_FAILED.name()));
        long lockouts = count(inPeriod, Criteria.where("type").is(SecurityEventType.ACCOUNT_LOCKED.name()));
        long changes = count(inPeriod, Criteria.where("type").in(ADMIN_CHANGES.stream().map(Enum::name).toList()));

        Query period = Query.query(inPeriod);
        long people = mongo.findDistinct(period, "userId", SecurityEvent.class, String.class).size();
        long addresses = mongo.findDistinct(period, "ipAddress", SecurityEvent.class, String.class).size();

        Aggregation byType = Aggregation.newAggregation(
                Aggregation.match(inPeriod),
                Aggregation.group("type").count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count"),
                Aggregation.limit(8));
        AggregationResults<Document> grouped = mongo.aggregate(byType, SecurityEvent.class, Document.class);
        List<EventCount> top = grouped.getMappedResults().stream()
                .map(row -> new EventCount(String.valueOf(row.get("_id")), row.get("count", Number.class).longValue()))
                .toList();

        return new AdminSummary(from, to, total, info, warning, critical, failures, signIns, failed, lockouts, changes,
                people, addresses, top);
    }

    // ------------------------------------------------------------------ free text search

    private static final int MAX_WORDS = 5;
    private static final int MAX_WORD_LENGTH = 80;
    private static final int MAX_MATCHING_ACCOUNTS = 100;

    /** Text fields of an event that a word is looked for in. */
    private static final List<String> EVENT_TEXT_FIELDS = List.of(
            "type", "ipAddress", "country", "region", "city", "userAgent", "deviceLabel", "deviceType", "os", "browser",
            "actor", "actorRole", "subjectMasked", "targetType", "targetId", "targetLabel", "requestMethod",
            "requestPath", "reason", "details.from", "details.to", "details.changed", "details.previousRole",
            "details.newRole", "details.source");

    /** Fields that hold an id, matched exactly. */
    private static final List<String> EVENT_ID_FIELDS = List.of("id", "userId", "actor", "sessionId", "deviceId");

    /** Text fields of an account that a word is looked for in, to find events about or by that person. */
    private static final List<String> ACCOUNT_TEXT_FIELDS = List.of(
            "firstName", "middleName", "lastName", "username", "email", "phoneNumber", "address1", "country");

    /**
     * Free text search, so an administrator can type anything. The text is split into words (at most five) and every word
     * must match something in the event: the event type, address, place, device, browser, system, the request, the reason,
     * the target, an id, or the name, username, email or phone of the person it happened to or who did it. Words are matched
     * anywhere in a field and ignore case. "failed", "succeeded", "critical", "warning" and "info" also match the result and
     * the severity. Returns null when there is nothing to search for.
     */
    private Criteria search(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        List<Criteria> perWord = new ArrayList<>();
        for (String word : q.trim().split("\\s+")) {
            if (word.isBlank()) {
                continue;
            }
            perWord.add(wordCriteria(word.length() > MAX_WORD_LENGTH ? word.substring(0, MAX_WORD_LENGTH) : word));
            if (perWord.size() == MAX_WORDS) {
                break;
            }
        }
        if (perWord.isEmpty()) {
            return null;
        }
        return perWord.size() == 1 ? perWord.get(0) : new Criteria().andOperator(perWord.toArray(new Criteria[0]));
    }

    private Criteria wordCriteria(String word) {
        Pattern pattern = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE);
        List<Criteria> any = new ArrayList<>();
        for (String field : EVENT_TEXT_FIELDS) {
            any.add(Criteria.where(field).regex(pattern));
        }
        for (String field : EVENT_ID_FIELDS) {
            any.add(Criteria.where(field).is(word));
        }
        switch (word.toLowerCase(Locale.ROOT)) {
            case "critical" -> any.add(Criteria.where("severity").is(SecuritySeverity.CRITICAL.name()));
            case "warning", "warn" -> any.add(Criteria.where("severity").is(SecuritySeverity.WARNING.name()));
            case "info", "information" -> any.add(Criteria.where("severity").is(SecuritySeverity.INFO.name()));
            case "failed", "failure", "fail" -> any.add(Criteria.where("outcome").is(SecurityOutcome.FAILURE.name()));
            case "succeeded", "success", "ok" -> any.add(Criteria.where("outcome").is(SecurityOutcome.SUCCESS.name()));
            default -> { }
        }
        List<String> accounts = matchingAccounts(pattern);
        if (!accounts.isEmpty()) {
            any.add(Criteria.where("userId").in(accounts));
            any.add(Criteria.where("actor").in(accounts));
        }
        return new Criteria().orOperator(any.toArray(new Criteria[0]));
    }

    /** The ids of accounts with a name, username, email, phone or address that contains the word. */
    private List<String> matchingAccounts(Pattern pattern) {
        List<Criteria> fields = new ArrayList<>();
        for (String field : ACCOUNT_TEXT_FIELDS) {
            fields.add(Criteria.where(field).regex(pattern));
        }
        Query accounts = new Query(new Criteria().orOperator(fields.toArray(new Criteria[0])));
        accounts.fields().include("id");
        accounts.limit(MAX_MATCHING_ACCOUNTS);
        return mongo.find(accounts, User.class).stream().map(User::getId).toList();
    }

    // ------------------------------------------------------------------

    private long count(Criteria... criteria) {
        Query query = new Query();
        for (Criteria c : criteria) {
            query.addCriteria(c);
        }
        return mongo.count(query, SecurityEvent.class);
    }

    /** An ISO instant, or a date (start of that day, or with {@code endOfDay} the start of the next day, so the whole day is included). */
    private static Instant parse(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.length() <= 10) {
                LocalDate day = LocalDate.parse(text);
                return (endOfDay ? day.plusDays(1) : day).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Dates must look like 2026-11-18 or 2026-11-18T09:30:00Z.");
        }
    }

    /** Turns records into views, looking up the names and emails of the accounts involved in one query. */
    private List<AdminEventView> views(List<SecurityEvent> events) {
        Set<String> ids = new LinkedHashSet<>();
        for (SecurityEvent e : events) {
            addId(ids, e.getUserId());
            addId(ids, e.getActor());
        }
        Map<String, User> people = new HashMap<>();
        if (!ids.isEmpty()) {
            for (User u : mongo.find(Query.query(Criteria.where("id").in(ids)), User.class)) {
                people.put(u.getId(), u);
            }
        }
        return events.stream().map(e -> {
            User subject = people.get(e.getUserId());
            User by = people.get(e.getActor());
            return new AdminEventView(e.getId(), e.getType().name(),
                    e.getSeverity() == null ? null : e.getSeverity().name(),
                    e.getOutcome() == null ? null : e.getOutcome().name(),
                    e.getUserId(), name(subject), subject == null ? null : subject.getEmail(),
                    e.getActor(), name(by), by == null ? null : by.getEmail(),
                    e.getActorRole() != null ? e.getActorRole() : (by == null || by.getRole() == null ? null : by.getRole().name()),
                    e.getSubjectMasked(), e.getIpAddress(), e.getCountry(), e.getRegion(), e.getCity(),
                    e.getUserAgent(), e.getDeviceLabel(), e.getDeviceType(), e.getOs(), e.getBrowser(),
                    e.getSessionId(), e.getDeviceId(),
                    e.getTargetType(), e.getTargetId(), e.getTargetLabel(),
                    e.getRequestMethod(), e.getRequestPath(),
                    e.getReason(), e.getDetails(), e.getCreatedAt());
        }).toList();
    }

    private static void addId(Set<String> ids, String id) {
        if (id != null && !id.isBlank() && !NOT_AN_ACCOUNT.contains(id)) {
            ids.add(id);
        }
    }

    private static String name(User user) {
        if (user == null) {
            return null;
        }
        String full = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return full.isEmpty() ? user.getDisplayUsername() : full;
    }
}
