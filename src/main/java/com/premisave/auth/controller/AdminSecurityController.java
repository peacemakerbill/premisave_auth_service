package com.premisave.auth.controller;

import com.premisave.auth.dto.security.SecurityDtos.AdminEventView;
import com.premisave.auth.entity.SecurityEvent;
import com.premisave.auth.enums.SecurityEventType;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of the audit trail for administrators (/admin/** requires the ADMIN role).
 * There is no endpoint that edits or deletes events.
 */
@RestController
@RequestMapping("/admin/security")
public class AdminSecurityController {

    private final MongoTemplate mongo;

    public AdminSecurityController(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @GetMapping("/events")
    public List<AdminEventView> events(@RequestParam(required = false) String userId,
                                       @RequestParam(required = false) SecurityEventType type,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Query query = new Query();
        if (userId != null && !userId.isBlank()) {
            query.addCriteria(Criteria.where("userId").is(userId));
        }
        if (type != null) {
            query.addCriteria(Criteria.where("type").is(type.name()));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) Math.max(page, 0) * safeSize)
                .limit(safeSize);
        return mongo.find(query, SecurityEvent.class).stream()
                .map(e -> new AdminEventView(e.getId(), e.getType().name(),
                        e.getSeverity() == null ? null : e.getSeverity().name(), e.getUserId(), e.getActor(),
                        e.getSubjectMasked(), e.getIpAddress(), e.getSessionId(), e.getDeviceId(), e.getReason(),
                        e.getDetails(), e.getCreatedAt()))
                .toList();
    }
}
