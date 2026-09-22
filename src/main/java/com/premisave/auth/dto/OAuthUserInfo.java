package com.premisave.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized user info produced by every OAuth provider client after the
 * provider's token or code has been verified server-side.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthUserInfo {
    private String provider;          // "google", "facebook" or "github"
    private String providerId;        // Google sub / Facebook user id / GitHub numeric id
    private String email;             // Always a provider-verified email
    private String firstName;
    private String lastName;
    private String fullName;          // Used when the provider has no separate first/last name
    private String usernameHint;      // Preferred username (GitHub login), optional
    private String profilePictureUrl; // Provider-hosted picture URL, optional
}