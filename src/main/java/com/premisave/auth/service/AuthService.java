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
import jakarta.annotation.PostConstruct;
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

    @Value("${backend.url:http://localhost:8080}")
    private String backendUrl;

    @Value("${email.activation.path:templates/activation-email.html}")
    private String activationEmailPath;

    @Value("${email.reset-password.path:templates/reset-password-email.html}")
    private String resetPasswordEmailPath;

    @Value("${email.support:support@premisave.com}")
    private String supportEmail;

    // Dashboard URLs from properties
    @Value("${dashboard.url.client:${frontend.url}/dashboard/client}")
    private String clientDashboardUrl;

    @Value("${dashboard.url.home-owner:${frontend.url}/dashboard/home-owner}")
    private String homeOwnerDashboardUrl;

    @Value("${dashboard.url.admin:${frontend.url}/dashboard/admin}")
    private String adminDashboardUrl;

    @Value("${dashboard.url.operations:${frontend.url}/dashboard/operations}")
    private String operationsDashboardUrl;

    @Value("${dashboard.url.finance:${frontend.url}/dashboard/finance}")
    private String financeDashboardUrl;

    @Value("${dashboard.url.support:${frontend.url}/dashboard/support}")
    private String supportDashboardUrl;

    private Map<Role, String> dashboardUrls;

    @PostConstruct
    public void init() {
        dashboardUrls = new HashMap<>();
        dashboardUrls.put(Role.CLIENT, clientDashboardUrl);
        dashboardUrls.put(Role.HOME_OWNER, homeOwnerDashboardUrl);
        dashboardUrls.put(Role.ADMIN, adminDashboardUrl);
        dashboardUrls.put(Role.OPERATIONS, operationsDashboardUrl);
        dashboardUrls.put(Role.FINANCE, financeDashboardUrl);
        dashboardUrls.put(Role.SUPPORT, supportDashboardUrl);
    }

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
        
        // Fix Language handling
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
        System.out.println("DEBUG: User saved to MongoDB with ID: " + user.getId());

        String activationToken = generateToken(user, TokenType.ACTIVATION);
        String activationLink = frontendUrl + "/verify/" + activationToken;
        
        // Prepare template data
        Map<String, String> templateData = new HashMap<>();
        templateData.put("activationLink", activationLink);
        templateData.put("supportEmail", supportEmail);
        templateData.put("currentYear", String.valueOf(Year.now().getValue()));
        
        String emailContent = processEmailTemplate(activationEmailPath, templateData);

        // Use RabbitMQ to queue the email
        emailService.queueEmail(
                user.getEmail(),
                "Activate Your Premisave Account",
                emailContent
        );
        System.out.println("DEBUG: Email queued for: " + user.getEmail());

        AuthResponse response = new AuthResponse();
        response.setToken(jwtService.generateToken(user));
        response.setRole(user.getRole().name());
        response.setRedirectUrl(getDashboardUrl(user.getRole()));
        return response;
    }

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

            // Update last login timestamp
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            // Cache user in Redis
            redisTemplate.opsForValue().set("user:" + user.getId(), user);

            AuthResponse response = new AuthResponse();
            response.setToken(jwtService.generateToken(user));
            response.setRole(user.getRole().name());
            response.setRedirectUrl(getDashboardUrl(user.getRole()));
            return response;
            
        } catch (BadCredentialsException e) {
            // Check if the email exists in the system
            boolean emailExists = userRepository.findByEmail(request.getEmail()).isPresent();
            
            if (emailExists) {
                throw new RuntimeException("Incorrect password. Please try again.");
            } else {
                throw new RuntimeException("No account found with this email. Please sign up first.");
            }
        } catch (Exception e) {
            // Re-throw other exceptions as they are
            throw e;
        }
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        try {
            // Extract username from refresh token
            String username = jwtService.extractUsername(request.getRefreshToken());
            
            if (username == null) {
                throw new RuntimeException("Invalid refresh token");
            }
            
            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Validate the refresh token
            if (!jwtService.isTokenValid(request.getRefreshToken(), user)) {
                throw new RuntimeException("Invalid or expired refresh token");
            }
            
            // Generate new access token
            String newAccessToken = jwtService.generateToken(user);
            
            // Update user's last login
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            
            // Cache updated user in Redis
            redisTemplate.opsForValue().set("user:" + user.getId(), user);
            
            AuthResponse response = new AuthResponse();
            response.setToken(newAccessToken);
            response.setRole(user.getRole().name());
            response.setRedirectUrl(getDashboardUrl(user.getRole()));
            
            return response;
            
        } catch (Exception e) {
            throw new RuntimeException("Token refresh failed: " + e.getMessage(), e);
        }
    }

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

    public void resendActivation(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isVerified()) {
            throw new RuntimeException("Account is already verified");
        }

        String activationToken = generateToken(user, TokenType.ACTIVATION);
        String activationLink = frontendUrl + "/verify/" + activationToken;
        
        // Prepare template data
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

    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with this email"));

        String resetToken = generateToken(user, TokenType.RESET_PASSWORD);
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
        
        // Prepare template data
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

    public void confirmResetPassword(ResetPasswordConfirmRequest request) {
        // Validate passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("New passwords do not match");
        }

        // Find and validate token
        Token token = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        if (token.isUsed() || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired or already been used");
        }

        if (token.getType() != TokenType.RESET_PASSWORD) {
            throw new RuntimeException("Invalid token type");
        }

        // Get user and update password
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Mark token as used
        token.setUsed(true);
        tokenRepository.save(token);

        // Clear cached user
        redisTemplate.delete("user:" + user.getId());
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
        
        // Clear cached user
        redisTemplate.delete("user:" + user.getId());
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
        System.out.println("DEBUG: Token saved for user: " + user.getId());
        return tokenValue;
    }

    private String getDashboardUrl(Role role) {
        return dashboardUrls.getOrDefault(role, frontendUrl + "/dashboard");
    }

    private String readEmailTemplate(String templatePath) {
        // Try with classpath prefix first
        Resource resource = resourceLoader.getResource("classpath:" + templatePath);
        
        // If not found, try without the prefix (in case it's already included)
        if (!resource.exists()) {
            resource = resourceLoader.getResource("classpath:/" + templatePath);
        }
        
        // If still not found, try as a file resource
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
    
    private String processEmailTemplate(String templatePath, Map<String, String> data) {
        String template = readEmailTemplate(templatePath);
        
        // Replace placeholders in the format {{placeholder}}
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            template = template.replace(placeholder, entry.getValue());
        }
        
        return template;
    }
}