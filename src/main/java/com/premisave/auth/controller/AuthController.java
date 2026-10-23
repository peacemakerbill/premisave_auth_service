package com.premisave.auth.controller;

import com.premisave.auth.config.SecurityProperties;
import com.premisave.auth.dto.*;
import com.premisave.auth.service.AuthService;
import com.premisave.auth.service.OAuthService;
import com.premisave.auth.service.security.AuthSessionService.Established;
import com.premisave.auth.service.security.ClientContext;
import com.premisave.auth.service.security.ClientContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.premisave.auth.exception.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    /** Shown for every signup in neutral mode, so it must read well and say nothing about the address. */
    static final String SIGNUP_ACCEPTED_MESSAGE =
            "Thanks for signing up! We've sent an email to the address you entered. "
                    + "Please open it to continue. If you don't see it within a few minutes, check your spam folder.";

    private final AuthService authService;
    private final OAuthService oAuthService;
    private final ClientContextResolver clientContext;
    private final SecurityProperties securityProperties;

    public AuthController(AuthService authService, OAuthService oAuthService, ClientContextResolver clientContext,
                          SecurityProperties securityProperties) {
        this.authService = authService;
        this.oAuthService = oAuthService;
        this.clientContext = clientContext;
        this.securityProperties = securityProperties;
    }

    /** User Registration. Any "role" in the body is ignored: new accounts are always CLIENT. */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request,
                                    HttpServletRequest http, HttpServletResponse response) {
        ClientContext ctx = clientContext.resolve(http);
        if (securityProperties.isHideExistingAccounts()) {
            authService.register(request, ctx);
            return ResponseEntity.accepted().body(Map.of("message",
                    SIGNUP_ACCEPTED_MESSAGE));
        }
        return respond(authService.signup(request, ctx), response);
    }

    /** User Login */
    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(@Valid @RequestBody AuthRequest request,
                                               HttpServletRequest http, HttpServletResponse response) {
        return respond(authService.signin(request, clientContext.resolve(http)), response);
    }

    /**
     * Google sign-in / sign-up.
     *
     * POST /auth/google
     * Body: { "token": "<Google ID token>" }
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleSignin(@Valid @RequestBody SocialLoginRequest request,
                                                     HttpServletRequest http, HttpServletResponse response) {
        return respond(oAuthService.handleOAuth("google", request.getToken(), clientContext.resolve(http)), response);
    }

    /**
     * Facebook sign-in / sign-up.
     *
     * POST /auth/facebook
     * Body: { "token": "<Facebook user access token>" }
     */
    @PostMapping("/facebook")
    public ResponseEntity<AuthResponse> facebookSignin(@Valid @RequestBody SocialLoginRequest request,
                                                       HttpServletRequest http, HttpServletResponse response) {
        return respond(oAuthService.handleOAuth("facebook", request.getToken(), clientContext.resolve(http)), response);
    }

    /**
     * GitHub sign-in / sign-up.
     *
     * POST /auth/github
     * Body: { "token": "<authorization code from the GitHub redirect, or a GitHub access token>" }
     */
    @PostMapping("/github")
    public ResponseEntity<AuthResponse> githubSignin(@Valid @RequestBody SocialLoginRequest request,
                                                     HttpServletRequest http, HttpServletResponse response) {
        return respond(oAuthService.handleOAuth("github", request.getToken(), clientContext.resolve(http)), response);
    }

    /** Logout: blacklists the JWT and ends its session */
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        LogoutResponse response = authService.logout(authHeader);
        return response.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.badRequest().body(response);
    }

    /** Refresh Token. Send the refresh token from sign-in; each one works once and returns the next. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request,
                                                     HttpServletRequest http, HttpServletResponse response) {
        return respond(authService.refreshToken(request, clientContext.resolve(http)), response);
    }

    /** Verify account via email token */
    @GetMapping("/verify/{token}")
    public ResponseEntity<Map<String, String>> verifyAccount(@PathVariable String token) {
        authService.verifyAccount(token);
        return message("Account verified successfully. You can now login.");
    }

    /** Resend activation email. The answer is the same whether or not the address has an account. */
    @PostMapping("/resend-activation")
    public ResponseEntity<Map<String, String>> resendActivation(@RequestParam String email) {
        try {
            authService.resendActivation(email);
            return message("If an unverified account exists for that address, a new activation email is on its way.");
        } catch (RuntimeException e) {
            // Handled by the global exception handler, which answers with JSON
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to resend activation email. Please try again.");
        }
    }

    /** Forgot Password: sends reset link. The answer is the same whether or not the address has an account. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return message("If an account exists for that address, a password reset link has been sent.");
    }

    /** Reset Password using token */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordConfirmRequest request) {
        authService.confirmResetPassword(request);
        return message("Password reset successfully");
    }

    /** Change Password (authenticated user) */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return message("Password changed successfully");
    }

    /** Every plain confirmation is answered as JSON: {"message": "..."} */
    private static ResponseEntity<Map<String, String>> message(String text) {
        return ResponseEntity.ok(Map.of("message", text));
    }

    private ResponseEntity<AuthResponse> respond(Established established, HttpServletResponse response) {
        clientContext.writeDeviceCookie(response, established.deviceToken());
        return ResponseEntity.ok(established.response());
    }
}