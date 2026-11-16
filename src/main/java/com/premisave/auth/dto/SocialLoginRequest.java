package com.premisave.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body shared by POST /auth/google, /auth/facebook and /auth/github.
 *
 *   { "token": "..." }
 *
 * Google:   the Google ID token
 * Facebook: the Facebook user access token
 * GitHub:   the authorization code returned to the redirect URI, or a GitHub access token
 *
 * Google and Facebook can also be given an authorization code instead, for apps that cannot
 * run the provider's own sign-in (Linux and Windows desktop):
 *
 *   { "token": "<authorization code>", "redirectUri": "<the redirect_uri used on the authorize request>" }
 *
 * When redirectUri is present the token is treated as a code and exchanged here, so the
 * provider secret never leaves the server. The redirect URI must be on oauth.allowed-redirect-uris.
 */
@Data
public class SocialLoginRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @Size(max = 300, message = "Redirect URI is too long")
    private String redirectUri;
}