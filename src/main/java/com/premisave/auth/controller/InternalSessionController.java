package com.premisave.auth.controller;

import com.premisave.auth.service.security.SessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * For other Premisave services that want revoked sessions to stop working immediately, instead of
 * only checking the token signature. Guarded by ApiKeyFilter (X-API-Key) like the rest of /internal/**.
 *
 * GET /internal/sessions/{sid}?userId={userId} returns { "active": true|false }
 * The sid and userId are claims of the access token ("sid" and "userId").
 */
@RestController
@RequestMapping("/internal/sessions")
public class InternalSessionController {

    private final SessionService sessions;

    public InternalSessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/{sid}")
    public Map<String, Object> isActive(@PathVariable String sid, @RequestParam String userId) {
        return Map.of("active", sessions.isActive(sid, userId));
    }
}
