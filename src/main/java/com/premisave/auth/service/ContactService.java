package com.premisave.auth.service;

import com.premisave.auth.dto.ContactRequest;
import com.premisave.auth.dto.ContactResponse;
import com.premisave.auth.entity.ContactMessage;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.ContactTopic;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecuritySeverity;
import com.premisave.auth.exception.ApiException;
import com.premisave.auth.repository.ContactMessageRepository;
import com.premisave.auth.repository.UserRepository;
import com.premisave.auth.service.security.ClientContext;
import com.premisave.auth.service.security.ContactGuard;
import com.premisave.auth.service.security.EmailTemplateRenderer;
import com.premisave.auth.service.security.EmailTemplateRenderer.Row;
import com.premisave.auth.service.security.SecurityAuditService;
import com.premisave.auth.service.security.Tokens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles messages from the Contact us form: validates, throttles, saves, emails the team (with the
 * sender as reply-to) and sends the sender a confirmation with a reference.
 *
 * The message is saved first. If the email to the team then fails the caller gets a clear 503, the
 * record is marked DELIVERY_FAILED, and nothing is lost.
 */
@Slf4j
@Service
public class ContactService {

    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final ContactMessageRepository repository;
    private final ContactGuard guard;
    private final EmailService emailService;
    private final EmailTemplateRenderer renderer;
    private final SecurityAuditService audit;
    private final UserRepository userRepository;

    @Value("${email.support:thepeacemakerske@gmail.com}")
    private String supportEmail;

    public ContactService(ContactMessageRepository repository, ContactGuard guard, EmailService emailService,
                          EmailTemplateRenderer renderer, SecurityAuditService audit, UserRepository userRepository) {
        this.repository = repository;
        this.guard = guard;
        this.emailService = emailService;
        this.renderer = renderer;
        this.audit = audit;
        this.userRepository = userRepository;
    }

    public ContactResponse submit(ContactRequest request, ClientContext ctx) {
        if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
            // Only automated form fillers see the hidden field. Answer as if it worked and do nothing.
            return new ContactResponse(newReference(), receivedText());
        }

        ContactTopic topic = ContactTopic.parse(request.getTopic())
                .orElseThrow(() -> ApiException.badRequest("Please choose a topic from the list."));

        String name = singleLine(request.getName());
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String text = request.getMessage().trim();

        guard.assertAllowed(email, ctx == null ? null : ctx.ip());

        String userId = currentUserId();
        ContactMessage saved = new ContactMessage();
        saved.setReference(newReference());
        saved.setName(name);
        saved.setEmail(email);
        saved.setTopic(topic);
        saved.setMessage(text);
        saved.setUserId(userId);
        saved.setClientHash(ctx == null || ctx.ip() == null ? null : Tokens.sha256Hex(ctx.ip()).substring(0, 16));
        saved.setStatus(ContactMessage.Status.RECEIVED);
        saved.setCreatedAt(Instant.now());
        saved = repository.save(saved);

        try {
            emailService.sendHtml(supportEmail, "[Premisave contact] " + topic.label() + " (" + saved.getReference() + ")",
                    teamEmail(saved), email);
        } catch (RuntimeException e) {
            log.error("Could not deliver contact message {}: {}", saved.getReference(), e.getMessage());
            saved.setStatus(ContactMessage.Status.DELIVERY_FAILED);
            repository.save(saved);
            audit.event(SecurityEventType.CONTACT_MESSAGE_FAILED)
                    .severity(SecuritySeverity.WARNING)
                    .user(userId)
                    .actor(userId == null ? "ANONYMOUS" : userId)
                    .client(ctx)
                    .detail("reference", saved.getReference())
                    .write();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "We could not deliver your message just now. Please try again in a few minutes, or email us at "
                            + supportEmail + ".");
        }

        saved.setStatus(ContactMessage.Status.DELIVERED);
        repository.save(saved);
        audit.event(SecurityEventType.CONTACT_MESSAGE_RECEIVED)
                .user(userId)
                .actor(userId == null ? "ANONYMOUS" : userId)
                .client(ctx)
                .detail("reference", saved.getReference())
                .detail("topic", topic.name())
                .write();

        sendConfirmation(saved);
        return new ContactResponse(saved.getReference(), receivedText());
    }

    private String teamEmail(ContactMessage m) {
        Map<String, String> vars = new HashMap<>();
        vars.put("title", "New contact message");
        vars.put("intro", m.getName() + " wrote in through the Contact us form. Reply to this email to answer them directly.");
        vars.put("preheader", m.getTopic().label() + " from " + m.getName());
        vars.put("message", m.getMessage());
        List<Row> rows = List.of(
                new Row("Reference", m.getReference()),
                new Row("From", m.getName()),
                new Row("Email", m.getEmail()),
                new Row("Topic", m.getTopic().label()),
                new Row("Account", m.getUserId() == null ? "Not signed in" : "Signed in (user " + m.getUserId() + ")"),
                new Row("Received", WHEN.format(m.getCreatedAt())));
        return renderer.render("contact-received", vars, rows).html();
    }

    /** Best effort: the message is already with the team, so a failure here is only logged. */
    private void sendConfirmation(ContactMessage m) {
        try {
            Map<String, String> vars = new HashMap<>();
            vars.put("name", firstName(m.getName()));
            vars.put("title", "We have your message");
            vars.put("intro", "Thank you for writing to us. The Premisave team has your message and will reply to this address.");
            vars.put("preheader", "Your reference is " + m.getReference() + ".");
            vars.put("message", m.getMessage());
            List<Row> rows = List.of(
                    new Row("Reference", m.getReference()),
                    new Row("Topic", m.getTopic().label()));
            String html = renderer.render("contact-confirmation", vars, rows).html();
            emailService.sendEmail(m.getEmail(), "We received your message (" + m.getReference() + ")", html);
        } catch (RuntimeException e) {
            log.warn("Could not send the contact confirmation for {}: {}", m.getReference(), e.getMessage());
        }
    }

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }

    private static String receivedText() {
        return "Thank you, your message is with the Premisave team. We will reply by email.";
    }

    private static String newReference() {
        StringBuilder out = new StringBuilder("PRM-");
        for (int i = 0; i < 8; i++) {
            out.append(REFERENCE_ALPHABET.charAt(RANDOM.nextInt(REFERENCE_ALPHABET.length())));
        }
        return out.toString();
    }

    /** Collapses line breaks and control characters so a name can never break a header or a heading. */
    private static String singleLine(String value) {
        return value.replaceAll("[\\p{Cntrl}]+", " ").replaceAll("\\s{2,}", " ").trim();
    }

    private static String firstName(String name) {
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }
}
