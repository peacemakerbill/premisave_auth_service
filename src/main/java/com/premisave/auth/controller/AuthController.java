package com.premisave.auth.controller;

import com.premisave.auth.dto.*;
import com.premisave.auth.service.AuthService;
import com.premisave.auth.service.OAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final OAuthService oAuthService;

    public AuthController(AuthService authService, OAuthService oAuthService) {
        this.authService = authService;
        this.oAuthService = oAuthService;
    }

    /** User Registration */
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    /** User Login */
    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.signin(request));
    }

    /**
     * Google sign-in / sign-up.
     *
     * POST /auth/google
     * Body: { "token": "<Google ID token>" }
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleSignin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(oAuthService.handleOAuth("google", request.getToken()));
    }

    /**
     * Facebook sign-in / sign-up.
     *
     * POST /auth/facebook
     * Body: { "token": "<Facebook user access token>" }
     */
    @PostMapping("/facebook")
    public ResponseEntity<AuthResponse> facebookSignin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(oAuthService.handleOAuth("facebook", request.getToken()));
    }

    /**
     * GitHub sign-in / sign-up.
     *
     * POST /auth/github
     * Body: { "token": "<authorization code from the GitHub redirect, or a GitHub access token>" }
     */
    @PostMapping("/github")
    public ResponseEntity<AuthResponse> githubSignin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(oAuthService.handleOAuth("github", request.getToken()));
    }

    /** Logout: blacklists the JWT */
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        LogoutResponse response = authService.logout(authHeader);
        return response.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.badRequest().body(response);
    }

    /** Refresh Token */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    /** Verify account via email token */
    @GetMapping("/verify/{token}")
    public ResponseEntity<String> verifyAccount(@PathVariable String token) {
        authService.verifyAccount(token);
        return ResponseEntity.ok("Account verified successfully. You can now login.");
    }

    /** Resend activation email */
    @PostMapping("/resend-activation")
    public ResponseEntity<String> resendActivation(@RequestParam String email) {
        try {
            authService.resendActivation(email);
            return ResponseEntity.ok("Activation email resent successfully. Please check your inbox.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to resend activation email. Please try again.");
        }
    }

    /** Forgot Password: sends reset link */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok("Password reset link sent to your email");
    }

    /** Reset Password using token */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordConfirmRequest request) {
        authService.confirmResetPassword(request);
        return ResponseEntity.ok("Password reset successfully");
    }

    /** Change Password (authenticated user) */
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok("Password changed successfully");
    }
}