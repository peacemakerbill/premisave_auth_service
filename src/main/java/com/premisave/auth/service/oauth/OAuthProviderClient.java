package com.premisave.auth.service.oauth;

import com.premisave.auth.dto.OAuthUserInfo;

/**
 * One implementation per OAuth provider. Each implementation verifies the
 * provider credential server-side and returns normalized user info with a
 * provider-verified email, or throws a RuntimeException with a clear message.
 */
public interface OAuthProviderClient {

    /** Lower-case provider key: "google", "facebook" or "github". */
    String provider();

    /** @param token the single credential sent by the client (ID token, access token or GitHub code) */
    OAuthUserInfo fetchUser(String token);
}