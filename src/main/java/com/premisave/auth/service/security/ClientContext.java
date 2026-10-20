package com.premisave.auth.service.security;

/**
 * What the server knows about the caller of one request.
 *
 * @param ip              address from the connection, or from a trusted proxy (see ClientIpResolver)
 * @param userAgent       raw User-Agent header; an unverified hint that is easy to spoof
 * @param deviceToken     opaque device token from the cookie or header, or null
 * @param tokenInBody     true when a native app asked to receive a new device token in the response body
 */
public record ClientContext(String ip, String userAgent, String deviceToken, boolean tokenInBody) {

    public static ClientContext unknown() {
        return new ClientContext(null, null, null, false);
    }
}
