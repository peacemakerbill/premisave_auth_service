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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
     * The trail, newest first. Every filter is optional: userId (the account), actor (who did it), type, severity, outcome,
     * ip, and from / to (an ISO date such as 2026-11-18 or an instant).
     */
    @GetMapping("/events")
    public List<AdminEventView> events(@RequestParam(required = false) String userId,
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
        AggregationResults<Map> grouped = mongo.aggregate(byType, SecurityEvent.class, Map.class);
        List<EventCount> top = grouped.getMappedResults().stream()
                .map(row -> new EventCount(String.valueOf(row.get("_id")), ((Number) row.get("count")).longValue()))
                .toList();

        return new AdminSummary(from, to, total, info, warning, critical, failures, signIns, failed, lockouts, changes,
                people, addresses, top);
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
