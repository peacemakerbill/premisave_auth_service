package com.premisave.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Body shared by POST /auth/google, /auth/facebook and /auth/github.
 *
 *   { "token": "..." }
 *
 * Google:   the Google ID token
 * Facebook: the Facebook user access token
 * GitHub:   the authorization code returned to the redirect URI, or a GitHub access token
 */
@Data
public class SocialLoginRequest {

    @NotBlank(message = "Token is required")
    private String token;
}