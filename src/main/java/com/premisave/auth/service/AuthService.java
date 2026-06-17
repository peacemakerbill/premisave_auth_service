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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
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

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public AuthService(UserRepository userRepository,
                       TokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       EmailService emailService,
                       RedisTemplate<String, Object> redisTemplate) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
        this.redisTemplate = redisTemplate;
    }

    public AuthResponse signup(SignupRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already taken");
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

        String activationToken = generateToken(user, TokenType.ACTIVATION);
        emailService.sendVerificationEmail(user.getEmail(), activationToken);

        AuthResponse response = new AuthResponse();
        response.setToken(jwtService.generateToken(user));   // Now includes userId
        response.setRole(user.getRole().name());
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

            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            redisTemplate.opsForValue().set("user:" + user.getId(), user);

            AuthResponse response = new AuthResponse();
            response.setToken(jwtService.generateToken(user));   // Now includes userId
            response.setRole(user.getRole().name());
            return response;
            
        } catch (BadCredentialsException e) {
            boolean emailExists = userRepository.findByEmail(request.getEmail()).isPresent();
            if (emailExists) {
                throw new RuntimeException("Incorrect password. Please try again.");
            } else {
                throw new RuntimeException("No account found with this email. Please sign up first.");
            }
        }
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
                    expirationTime, 
                    java.util.concurrent.TimeUnit.MILLISECONDS
                );
            }

            User user = userRepository.findByEmail(username).orElse(null);
            if (user != null) {
                redisTemplate.delete("user:" + user.getId());
            }

            return new LogoutResponse("Logged out successfully", true);

        } catch (Exception e) {
            return new LogoutResponse("Logout failed: " + e.getMessage(), false);
        }
    }

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
            response.setToken(jwtService.generateToken(user));   // Now includes userId
            response.setRole(user.getRole().name());
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
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            throw new RuntimeException("Please provide a valid email address");
        }

        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new RuntimeException("No account found with this email"));

        if (user.isVerified()) {
            throw new RuntimeException("Account is already verified. You can proceed to login.");
        }

        if (!user.isActive()) {
            throw new RuntimeException("Account is deactivated. Please contact support.");
        }

        String activationToken = generateToken(user, TokenType.ACTIVATION);
        emailService.sendVerificationEmail(email, activationToken);
    }

    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No account found with this email"));

        String resetToken = generateToken(user, TokenType.RESET_PASSWORD);
        emailService.sendResetPasswordEmail(user.getEmail(), resetToken);
    }

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