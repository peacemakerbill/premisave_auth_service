package com.premisave.auth.service.oauth;

import java.util.Map;

/** Small helpers shared by the OAuth provider clients. */
final class OAuthUtils {

    private OAuthUtils() {
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}