package com.premisave.auth.service.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The redirect addresses a client may use when it hands over an authorization code.
 *
 * The provider only returns a code to an address registered on its own console, and it checks the
 * address again on the exchange, so this is a second lock on the same door: this server never
 * exchanges a code for an address nobody listed here. Comma-separated, exact match.
 */
@Component
public class OAuthRedirectPolicy {

    private final Set<String> allowed;

    public OAuthRedirectPolicy(@Value("${oauth.allowed-redirect-uris:http://localhost:3000/auth.html}") String list) {
        this.allowed = Arrays.stream(list.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Returns the trimmed address, or throws when it is not on the list. */
    public String require(String redirectUri) {
        String value = redirectUri == null ? "" : redirectUri.trim();
        if (!allowed.contains(value)) {
            throw new RuntimeException("This redirect address is not allowed for sign-in");
        }
        return value;
    }
}
