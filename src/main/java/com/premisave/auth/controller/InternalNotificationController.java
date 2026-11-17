package com.premisave.auth.controller;

import com.premisave.auth.dto.InternalEmailRequest;
import com.premisave.auth.service.InternalEmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lets another Premisave service email one of our users. The caller names the user by id and never sees the address.
 *
 * Protected by ApiKeyFilter (X-API-Key header), like every /internal/** endpoint. It is not reachable by end users.
 *
 * POST /internal/notifications/email
 * Header: X-API-Key: the shared service key
 * Body:   { "userId", "subject", "title", "message", "details": {"Group": "Team"}, "buttonLabel", "buttonPath" }
 *
 * Answers 202 as soon as the email is queued. Whether it was delivered is not reported: a person without a
 * verified account, or a mail provider that is down, must never break the action that caused the email.
 */
@Slf4j
@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

    private final InternalEmailService emailService;

    public InternalNotificationController(InternalEmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/email")
    public ResponseEntity<Map<String, Object>> email(@RequestBody InternalEmailRequest request) {
        if (!InternalEmailService.isValid(request)) {
            return ResponseEntity.badRequest().body(Map.<String, Object>of("queued", false, "message", "userId, subject and message are required"));
        }
        emailService.emailUser(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.<String, Object>of("queued", true));
    }
}
