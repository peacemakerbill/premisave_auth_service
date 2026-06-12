package com.premisave.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized user info extracted from a Google ID token or Facebook access token.
 * Both providers are mapped into this common structure before account lookup/creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthUserInfo {
    private String providerId;       // Google sub / Facebook user id
    private String email;
    private String firstName;
    private String lastName;
    private String profilePictureUrl;
    private String provider;         // "google" or "facebook"
}