package com.premisave.auth.service;

import com.premisave.auth.config.SecurityProperties;
import com.premisave.auth.dto.*;
import com.premisave.auth.entity.Token;
import com.premisave.auth.entity.User;
import com.premisave.auth.entity.UserSession;
import com.premisave.auth.enums.Language;
import com.premisave.auth.enums.LoginMethod;
import com.premisave.auth.enums.Role;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecuritySeverity;
import com.premisave.auth.enums.TokenType;
import com.premisave.auth.exception.ApiException;
import com.premisave.auth.repository.TokenRepository;
import com.premisave.auth.repository.UserRepository;
import com.premisave.auth.security.JwtService;
import com.premisave.auth.service.security.AdminAccessService;
import com.premisave.auth.service.security.AdminEmailPolicy;
import com.premisave.auth.service.security.AuthSessionService;
import com.premisave.auth.service.security.AuthSessionService.Established;
import com.premisave.auth.service.security.ClientContext;
import com.premisave.auth.service.security.LoginThrottleService;
import com.premisave.auth.service.security.PasswordChangeSecurityHandler;
import com.premisave.auth.service.security.PasswordResetService;
import com.premisave.auth.service.security.RequestAttributes;
import com.premisave.auth.service.security.SecurityAuditService;
import com.premisave.auth.service.security.SecurityNotificationService;
import com.premisave.auth.service.security.SessionService;
import com.premisave.auth.service.security.Tokens;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    private static final String GENERIC_SIGN_IN_ERROR = "Invalid email or password.";
    private static final String INVALID_REFRESH = "Invalid or expired refresh token";

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UsernameService usernameService;
    private final AuthSessionService authSessions;
    private final LoginThrottleService throttle;
    private final AdminAccessService adminAccess;
    private final SessionService sessionService;
    private final PasswordResetService passwordReset;
    private final PasswordChangeSecurityHandler passwordChanges;
    private final SecurityAuditService audit;
    private final SecurityNotificationService notifications;
    private final SecurityProperties securityProperties;

    public AuthService(UserRepository userRepository,
                       TokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       EmailService emailService,
                       RedisTemplate<String, Object> redisTemplate,
                       UsernameService usernameService,
                       AuthSessionService authSessions,
                       LoginThrottleService throttle,
                       AdminAccessService adminAccess,
                       SessionService sessionService,
                       PasswordResetService passwordReset,
                       PasswordChangeSecurityHandler passwordChanges,
                       SecurityAuditService audit,
                       SecurityNotificationService notifications,
                       SecurityProperties securityProperties) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
        this.redisTemplate = redisTemplate;
        this.usernameService = usernameService;
        this.authSessions = authSessions;
        this.throttle = throttle;
        this.adminAccess = adminAccess;
        this.sessionService = sessionService;
        this.passwordReset = passwordReset;
        this.passwordChanges = passwordChanges;
        this.audit = audit;
        this.notifications = notifications;
        this.securityProperties = securityProperties;
    }

    public Established signup(SignupRequest request, ClientContext ctx) {
        String email = request.getEmail().trim();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists");
        }
        User user = createAccount(request, email);

        // Registration opens a session (so the app can show "check your email") but sends no sign-in alert.
        return authSessions.establish(user, ctx, LoginMethod.SIGNUP, false, false);
    }

    /**
     * Registration that does not reveal whether the address is already registered. A new address gets an
     * account and a verification email; an existing one gets a notice. The caller sees no difference, and
     * no tokens are issued either way: the person signs in after verifying.
     */
    public void register(SignupRequest request, ClientContext ctx) {
        String email = request.getEmail().trim();
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            // Pay the same password-hashing cost as a real registration, so response time gives nothing away.
            passwordEncoder.encode(request.getPassword());
            noticeExistingOwner(existing.get(), ctx);
            return;
        }
        createAccount(request, email);
    }

    private void noticeExistingOwner(User owner, ClientContext ctx) {
        try {
            // At most one notice per hour per account, so signup cannot be used to flood someone's inbox.
            Boolean first = redisTemplate.opsForValue().setIfAbsent("signup-notice:" + owner.getId(), "1", Duration.ofHours(1));
            if (!Boolean.TRUE.equals(first)) {
                return;
            }
            notifications.sendNotice(owner.getId(), owner.getEmail(), owner.getFirstName(),
                    "Someone tried to register with your email address",
                    "Sign-up attempt with your email address",
                    "Someone tried to create a Premisave account with your email address, but you already have one. "
                            + "If this was you, sign in instead, or reset your password if you have forgotten it. "
                            + "If it was not you, you can ignore this email: your account has not been changed.",
                    java.util.List.of(new com.premisave.auth.service.security.EmailTemplateRenderer.Row("IP address", ctx.ip())),
                    "", "", "");
            audit.event(SecurityEventType.SIGN_UP_EXISTING_EMAIL)
                    .user(owner.getId()).client(ctx)
                    .write();
        } catch (RuntimeException e) {
            // Nothing the caller can act on, and the answer must not differ from a normal registration.
            log.warn("Existing-owner notice skipped: {}", e.getClass().getSimpleName());
        }
    }

    private User createAccount(SignupRequest request, String email) {
        String requestedUsername = request.getUsername() == null ? "" : request.getUsername().trim();
        boolean hasUsername = !requestedUsername.isEmpty();
        if (hasUsername && usernameService.isTaken(requestedUsername)) {
            throw new RuntimeException("Username already taken");
        }

        User user = new User();
        // No username given: it stays empty for a moment and is generated in the background below
        user.setUsername(hasUsername ? requestedUsername : null);
        user.setUsernameGenerated(false);
        user.setFirstName(request.getFirstName());
        user.setMiddleName(request.getMiddleName());
        user.setLastName(request.getLastName());
        user.setEmail(email);
        user.setPhoneNumber(request.getPhoneNumber());
        user.setAddress1(request.getAddress1());
        user.setAddress2(request.getAddress2());
        user.setCountry(request.getCountry());

        try {
            Language language = Language.valueOf(request.getLanguage().toUpperCase());
            user.setLanguage(language);
        } catch (IllegalArgumentException | NullPointerException e) {
            user.setLanguage(Language.ENGLISH);
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // The role is never taken from the request. Administrator access is granted only by
        // AdminAccessService after the email is verified; other roles by staff workflows.
        user.setRole(Role.CLIENT);
        user.setVerified(false);
        user.setActive(true);

        user = userRepository.save(user);

        if (!hasUsername) {
            usernameService.assignInBackground(user.getId());
        }

        String activationToken = generateToken(user, TokenType.ACTIVATION);
        emailService.sendVerificationEmail(user.getEmail(), activationToken);
        return user;
    }

    public Established signin(AuthRequest request, ClientContext ctx) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim();
        throttle.assertAllowed(email, ctx.ip());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (DisabledException e) {
            // Spring checks "enabled" before the password. Reveal the reason only to someone who knows the password.
            throw disabledOrGeneric(email, request.getPassword(), ctx);
        } catch (AuthenticationException e) {
            throw failedSignIn(email, ctx);
        }

        User user = (User) authentication.getPrincipal();
        throttle.recordSuccess(email);

        // Applies a changed AUTO_ADMIN_EMAILS list to an existing verified account before the token is issued.
        adminAccess.grantIfEligible(user, "SIGN_IN");

        LocalDateTime previousLogin = user.getLastLoginAt();
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        redisTemplate.opsForValue().set("user:" + user.getId(), user);

        return authSessions.establish(user, ctx, LoginMethod.PASSWORD, previousLogin != null, true);
    }

    private ApiException disabledOrGeneric(String email, String password, ClientContext ctx) {
        Optional<User> found = userRepository.findByEmail(email);
        if (found.isPresent() && password != null && passwordEncoder.matches(password, found.get().getPassword())) {
            User user = found.get();
            if (!user.isVerified()) {
                return new ApiException(HttpStatus.FORBIDDEN, "Account not verified. Please check your email.");
            }
            if (!user.isActive()) {
                return new ApiException(HttpStatus.FORBIDDEN, "Account is deactivated. Please contact support.");
            }
        }
        return failedSignIn(email, ctx);
    }

    /** Counts the failure, audits it, warns the owner when a lock starts, and answers with a message that reveals nothing. */
    private ApiException failedSignIn(String email, ClientContext ctx) {
        Optional<Duration> lock = throttle.recordFailure(email, ctx.ip());
        Optional<User> account = userRepository.findByEmail(email);

        SecurityAuditService.Builder event = audit.event(SecurityEventType.SIGN_IN_FAILED)
                .severity(SecuritySeverity.WARNING)
                .actor("ANONYMOUS")
                .client(ctx);
        if (account.isPresent()) {
            event.user(account.get().getId());
        } else {
            event.subject(AdminEmailPolicy.mask(email), Tokens.sha256Hex(email.toLowerCase()).substring(0, 16));
        }
        event.write();

        if (lock.isPresent()) {
            audit.event(SecurityEventType.ACCOUNT_LOCKED)
                    .severity(SecuritySeverity.WARNING)
                    .user(account.map(User::getId).orElse(null))
                    .actor("SYSTEM")
                    .client(ctx)
                    .detail("minutes", lock.get().toMinutes())
                    .write();
            account.ifPresent(u -> notifications.sendAccountLocked(
                    u.getId(), u.getEmail(), u.getFirstName(), ctx.ip(), (int) lock.get().toMinutes()));
        }
        return new ApiException(HttpStatus.UNAUTHORIZED, GENERIC_SIGN_IN_ERROR);
    }

    public LogoutResponse logout(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return new LogoutResponse("Invalid or missing Authorization header", false);
            }

            String jwt = authHeader.substring(7);

            String username = jwtService.extractUsername(jwt);
            if (username == null) {
                return new LogoutResponse("Invalid token", false);
            }

            Date expiration = jwtService.extractExpiration(jwt);
            long expirationTime = expiration.getTime() - System.currentTimeMillis();

            if (expirationTime > 0) {
                redisTemplate.opsForValue().set(
                    "blacklist:" + jwt,
                    true,
                    Duration.ofMillis(expirationTime)
                );
            }

            User user = userRepository.findByEmail(username).orElse(null);
            if (user != null) {
                redisTemplate.delete("user:" + user.getId());

                // End the server-side session too, so the refresh token stops working as well.
                String sessionId = jwtService.extractSessionId(jwt);
                if (sessionId != null && sessionService.revoke(sessionId, user.getId(), "LOGOUT", user.getId())) {
                    audit.event(SecurityEventType.SESSION_REVOKED)
                            .user(user.getId()).actor(user.getId())
                            .client(RequestAttributes.currentClient())
                            .session(sessionId).reason("Signed out")
                            .write();
                }
            }

            return new LogoutResponse("Logged out successfully", true);

        } catch (Exception e) {
            return new LogoutResponse("Logout failed: " + e.getMessage(), false);
        }
    }

    public Established refreshToken(RefreshTokenRequest request, ClientContext ctx) {
        String token = request.getRefreshToken();
        String email;
        String type;
        String sessionId;
        Integer generation;
        try {
            email = jwtService.extractUsername(token);
            type = jwtService.extractTokenType(token);
            sessionId = jwtService.extractSessionId(token);
            generation = jwtService.extractGeneration(token);
        } catch (RuntimeException e) {
            throw invalidRefresh();
        }
        if (email == null) {
            throw invalidRefresh();
        }

        User user = userRepository.findByEmail(email).orElseThrow(AuthService::invalidRefresh);
        if (!user.isActive() || user.isArchived()) {
            throw invalidRefresh();
        }

        if (JwtService.REFRESH.equals(type)) {
            return refreshWithRefreshToken(user, sessionId, generation, ctx);
        }
        return refreshLegacy(user, token, sessionId, ctx);
    }

    /** Normal path: the refresh token rotates, so every refresh token works exactly once. */
    private Established refreshWithRefreshToken(User user, String sessionId, Integer generation, ClientContext ctx) {
        if (sessionId == null || generation == null) {
            throw invalidRefresh();
        }
        Optional<UserSession> rotated = sessionService.rotate(sessionId, user.getId(), generation);
        if (rotated.isEmpty()) {
            handleRefreshReuse(user, sessionId, ctx);
            throw invalidRefresh();
        }
        UserSession session = rotated.get();

        redisTemplate.opsForValue().set("user:" + user.getId(), user);

        String role = user.getRole() != null ? user.getRole().name() : Role.CLIENT.name();
        AuthResponse response = new AuthResponse(
                jwtService.generateAccessToken(user, sessionId),
                role,
                jwtService.generateRefreshToken(user, sessionId, session.getRefreshGeneration(), session.getExpiresAt()));
        response.setExpiresIn(jwtService.accessTokenSeconds());
        return new Established(response, null, session);
    }

    /**
     * A refresh token that is not current: either its session was revoked (a signed-out or reported
     * session is being used) or an older token of a live session came back (a copied token was replayed).
     * In the second case the session is ended, which also invalidates the newer token the real owner holds.
     */
    private void handleRefreshReuse(User user, String sessionId, ClientContext ctx) {
        UserSession session = sessionService.find(sessionId).orElse(null);
        if (session == null || !user.getId().equals(session.getUserId())) {
            return;
        }
        boolean revoked = session.getRevokedAt() != null;
        boolean live = session.isActive(sessionService.now());
        if (!revoked && !live) {
            return;   // simply expired
        }

        audit.event(SecurityEventType.REFRESH_TOKEN_REUSE)
                .severity(SecuritySeverity.CRITICAL)
                .user(user.getId()).client(ctx).session(sessionId)
                .detail("sessionWasRevoked", revoked)
                .write();

        if (live && sessionService.revoke(sessionId, user.getId(), "REFRESH_TOKEN_REUSE", "SYSTEM")) {
            notifications.sendNotice(user.getId(), user.getEmail(), user.getFirstName(),
                    "A Premisave session was ended for your safety",
                    "A session was ended for your safety",
                    "An old sign-in token for your account was used again, which can mean it was copied. We ended that session so it cannot be used further.",
                    java.util.List.of(new com.premisave.auth.service.security.EmailTemplateRenderer.Row("IP address", ctx.ip())),
                    "If you do not recognise this, change your password and review your active sessions.",
                    "Review account security", notifications.securityUrl());
        }
    }

    /** Older clients send their access token to /auth/refresh. Accepted while legacy-refresh-enabled is true. */
    private Established refreshLegacy(User user, String token, String sessionId, ClientContext ctx) {
        if (!securityProperties.isLegacyRefreshEnabled() || !jwtService.isTokenValid(token, user)) {
            throw invalidRefresh();
        }
        if (sessionId != null) {
            if (!sessionService.isActive(sessionId, user.getId())) {
                throw invalidRefresh();
            }
            redisTemplate.opsForValue().set("user:" + user.getId(), user);
            String role = user.getRole() != null ? user.getRole().name() : Role.CLIENT.name();
            AuthResponse response = new AuthResponse(jwtService.generateAccessToken(user, sessionId), role);
            response.setExpiresIn(jwtService.accessTokenSeconds());
            return new Established(response, null, sessionService.find(sessionId).orElse(null));
        }
        // Issued before sessions existed: start a session now so it can be revoked from here on.
        redisTemplate.opsForValue().set("user:" + user.getId(), user);
        return authSessions.establish(user, ctx, LoginMethod.PASSWORD, true, false);
    }

    private static ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, INVALID_REFRESH);
    }

    public void verifyAccount(String tokenStr) {
        Token token = tokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new RuntimeException("Invalid or expired token"));

        if (token.getType() != TokenType.ACTIVATION) {
            throw new RuntimeException("Invalid or expired token");
        }
        if (token.isUsed() || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token has expired or already been used");
        }

        User user = token.getUser();
        user.setVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        // Verification is the moment an address becomes trusted, so configured admin emails are applied here.
        adminAccess.grantIfEligible(user, "EMAIL_VERIFICATION");
    }

    /** Silent when the address is unknown, already verified or deactivated, so it cannot be used to probe for accounts. */
    public void resendActivation(String email) {
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            throw new RuntimeException("Please provide a valid email address");
        }

        Optional<User> found = userRepository.findByEmail(email.trim().toLowerCase());
        if (found.isEmpty()) {
            found = userRepository.findByEmail(email.trim());
        }
        if (found.isEmpty() || found.get().isVerified() || !found.get().isActive()) {
            return;
        }
        User user = found.get();
        String activationToken = generateToken(user, TokenType.ACTIVATION);
        emailService.sendVerificationEmail(user.getEmail(), activationToken);
    }

    /** Always completes normally: an unknown address gets no email and no error. */
    public void forgotPassword(String email) {
        passwordReset.requestReset(email);
    }

    public void confirmResetPassword(ResetPasswordConfirmRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New passwords do not match");
        }
        if (request.getNewPassword().length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters long");
        }

        Token token = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        if (token.isUsed() || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired or already been used");
        }

        if (token.getType() != TokenType.RESET_PASSWORD) {
            throw new RuntimeException("Invalid token type");
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // The link was delivered to the account's own address, which proves ownership of it.
        user.setVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        redisTemplate.delete("user:" + user.getId());

        // A reset ends every session, including any the person who knew the old password had open.
        passwordChanges.afterPasswordChange(user, null, SecurityEventType.PASSWORD_RESET, user.getId(),
                "Password reset by email link", RequestAttributes.currentClient());
        adminAccess.grantIfEligible(user, "PASSWORD_RESET");
    }

    public void changePassword(ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) auth.getPrincipal();

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New passwords do not match");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        redisTemplate.delete("user:" + user.getId());

        // Other devices are signed out; this one stays signed in.
        passwordChanges.afterPasswordChange(user, RequestAttributes.currentSessionId(),
                SecurityEventType.PASSWORD_CHANGED, user.getId(), "Changed from account settings",
                RequestAttributes.currentClient());
    }

    private String generateToken(User user, TokenType type) {
        String tokenValue = UUID.randomUUID().toString();

        Token token = new Token();
        token.setToken(tokenValue);
        token.setType(type);
        token.setExpiryDate(LocalDateTime.now().plus(24, ChronoUnit.HOURS));
        token.setUsed(false);
        token.setUser(user);

        tokenRepository.save(token);
        return tokenValue;
    }
}
