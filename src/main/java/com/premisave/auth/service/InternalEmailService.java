package com.premisave.auth.service;

import com.premisave.auth.dto.InternalEmailRequest;
import com.premisave.auth.entity.User;
import com.premisave.auth.repository.UserRepository;
import com.premisave.auth.service.security.EmailTemplateRenderer;
import com.premisave.auth.service.security.EmailTemplateRenderer.Row;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends the emails other Premisave services ask for (see InternalNotificationController).
 *
 * Only a verified, active, non-archived account is emailed. Every text that came from the caller goes through
 * the template renderer, which escapes it, so a name inside a message cannot inject markup into the email.
 */
@Slf4j
@Service
public class InternalEmailService {

    private static final int MAX_SUBJECT = 150;
    private static final int MAX_TITLE = 120;
    private static final int MAX_MESSAGE = 600;
    private static final int MAX_DETAILS = 8;

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final EmailTemplateRenderer renderer;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public InternalEmailService(UserRepository userRepository, EmailService emailService, EmailTemplateRenderer renderer) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.renderer = renderer;
    }

    /** True when the request has what an email needs. */
    public static boolean isValid(InternalEmailRequest r) {
        return r != null
                && r.userId() != null && !r.userId().isBlank()
                && r.subject() != null && !r.subject().isBlank()
                && r.message() != null && !r.message().isBlank();
    }

    @Async
    public void emailUser(InternalEmailRequest request) {
        try {
            User user = userRepository.findById(request.userId()).orElse(null);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                log.debug("Internal email skipped: no address for user {}", request.userId());
                return;
            }
            if (user.isArchived() || !user.isActive() || !user.isVerified()) {
                log.debug("Internal email skipped: account {} is not active and verified", request.userId());
                return;
            }

            String subject = oneLine(request.subject(), MAX_SUBJECT);
            Map<String, String> vars = new HashMap<>();
            vars.put("name", greeting(user.getFirstName()));
            vars.put("title", oneLine(request.title() == null || request.title().isBlank() ? request.subject() : request.title(), MAX_TITLE));
            vars.put("intro", oneLine(request.message(), MAX_MESSAGE));
            vars.put("subject", subject);
            vars.put("preheader", oneLine(request.message(), 110));
            String path = safePath(request.buttonPath());
            if (path != null && request.buttonLabel() != null && !request.buttonLabel().isBlank()) {
                vars.put("buttonLabel", oneLine(request.buttonLabel(), 40));
                vars.put("buttonUrl", baseUrl() + path);
            }

            List<Row> rows = new ArrayList<>();
            if (request.details() != null) {
                request.details().forEach((label, value) -> {
                    if (rows.size() < MAX_DETAILS) rows.add(new Row(oneLine(label, 40), oneLine(value, 200)));
                });
            }

            String html = renderer.render("group-notice", vars, rows).html();
            emailService.sendHtml(user.getEmail(), subject, html);
        } catch (Exception e) {
            // Class name only: the exception text can carry the address or the mail provider's details.
            log.warn("Internal email for user {} could not be sent ({})", request.userId(), e.getClass().getSimpleName());
        }
    }

    private String baseUrl() {
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    /** Only a path on our own web app is accepted, so a caller cannot make the button open another site. */
    private static String safePath(String path) {
        if (path == null || path.isBlank()) return null;
        String p = path.trim();
        if (!p.startsWith("/") || p.startsWith("//") || p.contains("://") || p.contains("\\")) return null;
        return p.length() > 200 ? null : p;
    }

    private static String oneLine(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\p{Cntrl}", " ").trim();
        return clean.length() > max ? clean.substring(0, max) : clean;
    }

    private static String greeting(String firstName) {
        return firstName == null || firstName.isBlank() ? "there" : firstName.trim();
    }
}
