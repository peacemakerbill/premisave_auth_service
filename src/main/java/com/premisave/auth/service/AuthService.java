package com.premisave.auth.service;

import com.premisave.auth.dto.*;
import com.premisave.auth.entity.Token;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.Language;
import com.premisave.auth.enums.Role;
import com.premisave.auth.enums.TokenType;
import com.premisave.auth.repository.TokenRepository;
import com.premisave.auth.repository.UserRepository;
import com.premisave.auth.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ResourceLoader resourceLoader;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${email.activation.path:templates/activation-email.html}")
    private String activationEmailPath;

    @Value("${email.reset-password.path:templates/reset-password-email.html}")
    private String resetPasswordEmailPath;

    @Value("${email.support:support@premisave.com}")
    private String supportEmail;

    public AuthService(UserRepository userRepository,
                       TokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       EmailService emailService,
                       RedisTemplate<String, Object> redisTemplate,
                       ResourceLoader resourceLoader) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
        this.redisTemplate = redisTemplate;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Registers a new user and sends account activation email.
     */
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setFirstName(request.getFirstName());
        user.setMiddleName(request.getMiddleName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setAddress1(request.getAddress1());
        user.setAddress2(request.getAddress2());
        user.setCountry(request.getCountry());
        
        // Set language with fallback to ENGLISH
        try {
            Language language = Language.valueOf(request.getLanguage().toUpperCase());
            user.setLanguage(language);
        } catch (IllegalArgumentException e) {
            user.setLanguage(Language.ENGLISH);
        }
        
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole() != null ? request.getRole() : Role.CLIENT);
        user.setVerified(false);
        user.setActive(true);

        user = userRepository.save(user);

        // Generate and send activation token
        String activationToken = generateToken(user, TokenType.ACTIVATION);
        String activationLink = frontendUrl + "/verify/" + activationToken;
        
        Map<String, String> templateData = new HashMap<>();
        templateData.put("activationLink", activationLink);
        templateData.put("supportEmail", supportEmail);
        templateData.put("currentYear", String.valueOf(Year.now().getValue()));
        
        String emailContent = processEmailTemplate(activationEmailPath, templateData);

        emailService.queueEmail(
                user.getEmail(),
                "Activate Your Premisave Account",
                emailContent
        );

        // Return JWT token for immediate use after signup
        AuthResponse response = new AuthResponse();
        response.setToken(jwtService.generateToken(user));
        response.setRole(user.getRole().name());
        return response;
    }

    /**
     * Authenticates user and returns JWT token on successful login.
     */
    public AuthResponse signin(AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            User user = (User) authentication.getPrincipal();

            if (!user.isVerified()) {
                throw new RuntimeException("Account not verified. Please check your email.");
            }

            if (!user.isActive()) {
                throw new RuntimeException("Account is deactivated. Please contact support.");
            }

            // Update last login time
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            // Cache user in Redis
            redisTemplate.opsForValue().set("user:" + user.getId(), user);

            AuthResponse response = new AuthResponse();
            response.setToken(jwtService.generateToken(user));
            response.setRole(user.getRole().name());
            return response;
            
        } catch (BadCredentialsException e) {
            boolean emailExists = userRepository.findByEmail(request.getEmail()).isPresent();
            
            if (emailExists) {
                throw new RuntimeException("Incorrect password. Please try again.");
            } else {
                throw new RuntimeException("No account found with this email. Please sign up first.");
            }
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Refreshes expired JWT token using a valid refresh token.
     */
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        try {
            String username = jwtService.extractUsername(request.getRefreshToken());
            
            if (username == null) {
                throw new RuntimeException("Invalid refresh token");
            }
            
            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            if (!jwtService.isTokenValid(request.getRefreshToken(), user)) {
                throw new RuntimeException("Invalid or expired refresh token");
            }
            
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            
            redisTemplate.opsForValue().set("user:" + user.getId(), user);
            
            AuthResponse response = new AuthResponse();
            response.setToken(jwtService.generateToken(user));
            response.setRole(user.getRole().name());
            return response;
            
        } catch (Exception e) {
            throw new RuntimeException("Token refresh failed: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies user account using activation token.
     */
    public void verifyAccount(String tokenStr) {
        Token token = tokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new RuntimeException("Invalid or expired token"));

        if (token.isUsed() || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token has expired or already been used");
        }

        User user = token.getUser();
        user.setVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);
    }

    /**
     * Resends account activation email.
     */
    public void resendActivation(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isVerified()) {
            throw new RuntimeException("Account is already verified");
        }

        String activationToken = generateToken(user, TokenType.ACTIVATION);
        String activationLink = frontendUrl + "/verify/" + activationToken;
        
        Map<String, String> templateData = new HashMap<>();
        templateData.put("activationLink", activationLink);
        templateData.put("supportEmail", supportEmail);
        templateData.put("currentYear", String.valueOf(Year.now().getValue()));
        
        String emailContent = processEmailTemplate(activationEmailPath, templateData);
        emailService.queueEmail(
                email,
                "Activate Your Premisave Account",
                emailContent
        );
    }

    /**
     * Initiates password reset process by sending reset link via email.
     */
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with this email"));

        String resetToken = generateToken(user, TokenType.RESET_PASSWORD);
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
        
        Map<String, String> templateData = new HashMap<>();
        templateData.put("resetLink", resetLink);
        templateData.put("supportEmail", supportEmail);
        templateData.put("currentYear", String.valueOf(Year.now().getValue()));
        
        String emailContent = processEmailTemplate(resetPasswordEmailPath, templateData);
        emailService.queueEmail(
                user.getEmail(),
                "Reset Your Premisave Password",
                emailContent
        );
    }

    /**
     * Completes password reset using valid reset token.
     */
    public void confirmResetPassword(ResetPasswordConfirmRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New passwords do not match");
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
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        redisTemplate.delete("user:" + user.getId());
    }

    /**
     * Allows authenticated user to change their password.
     */
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
    }

    /**
     * Generates and saves activation or reset password token.
     */
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

    /**
     * Reads email template from resources.
     */
    private String readEmailTemplate(String templatePath) {
        Resource resource = resourceLoader.getResource("classpath:" + templatePath);
        
        if (!resource.exists()) {
            resource = resourceLoader.getResource("classpath:/" + templatePath);
        }
        
        if (!resource.exists()) {
            resource = resourceLoader.getResource("file:src/main/resources/" + templatePath);
        }
        
        if (!resource.exists()) {
            throw new RuntimeException("Email template not found at any location: " + templatePath);
        }
        
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read email template: " + templatePath, e);
        }
    }
    
    /**
     * Processes email template by replacing placeholders.
     */
    private String processEmailTemplate(String templatePath, Map<String, String> data) {
        String template = readEmailTemplate(templatePath);
        
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            template = template.replace(placeholder, entry.getValue());
        }
        
        return template;
    }
}