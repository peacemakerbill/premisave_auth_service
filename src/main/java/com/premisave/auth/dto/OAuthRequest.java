package com.premisave.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Body for POST /auth/oauth.
 *
 * Google:   { "provider": "google",   "token": "<Google ID token>" }
 * Facebook: { "provider": "facebook", "token": "<Facebook user access token>" }
 * GitHub:   { "provider": "github",   "code": "<authorization code>", "redirectUri": "<same redirect_uri used on GitHub>" }
 *       or: { "provider": "github",   "token": "<GitHub access token issued to this app>" }
 */
@Data
public class OAuthRequest {

    @NotBlank(message = "Provider is required")
    private String provider;     // "google", "facebook" or "github"

    private String token;        // Google ID token, Facebook access token, or GitHub access token

    private String code;         // GitHub authorization code (web redirect flow)

    private String redirectUri;  // GitHub redirect_uri used when the code was issued
}