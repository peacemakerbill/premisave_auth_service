package com.premisave.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized user info produced by every social provider client (Google, Facebook, GitHub)
 * before account lookup or creation. The email is always provider-verified.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthUserInfo {
    private String providerId;
    private String email;
    private String firstName;
    private String lastName;
    /** Display name as the provider reports it; used only when first and last name are missing. */
    private String fullName;
    private String profilePictureUrl;
    /** "google", "facebook" or "github". */
    private String provider;
    /** Provider handle (for example a GitHub login) used as a hint when generating a username. */
    private String usernameHint;
}
