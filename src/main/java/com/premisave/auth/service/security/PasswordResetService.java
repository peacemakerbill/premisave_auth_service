package com.premisave.auth.service.security;

import com.premisave.auth.entity.Token;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.TokenType;
import com.premisave.auth.repository.TokenRepository;
import com.premisave.auth.repository.UserRepository;
import com.premisave.auth.service.EmailService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Starts the existing password-reset flow (token in the tokens collection, emailed link).
 * Shared by "forgot password", the "this was not me" workflow and administrator resets,
 * so there is one place that creates reset tokens.
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final EmailService emailService;
    private final SecurityAuditService audit;

    public PasswordResetService(UserRepository userRepository, TokenRepository tokenRepository, EmailService emailService,
                                SecurityAuditService audit) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.audit = audit;
    }

    /** Sends a reset link if the address belongs to an account. Silent otherwise, so callers cannot probe for accounts. */
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim()).ifPresent(user -> {
            sendResetFor(user);
            audit.event(SecurityEventType.PASSWORD_RESET_REQUESTED).user(user.getId()).actor("ANONYMOUS")
                    .client(RequestAttributes.currentClient())
                    .reason("A reset link was requested for this account").write();
        });
    }

    public void sendResetFor(User user) {
        Token token = new Token();
        token.setToken(UUID.randomUUID().toString());
        token.setType(TokenType.RESET_PASSWORD);
        token.setExpiryDate(LocalDateTime.now().plus(24, ChronoUnit.HOURS));
        token.setUsed(false);
        token.setUser(user);
        tokenRepository.save(token);
        emailService.sendResetPasswordEmail(user.getEmail(), token.getToken());
    }
}
