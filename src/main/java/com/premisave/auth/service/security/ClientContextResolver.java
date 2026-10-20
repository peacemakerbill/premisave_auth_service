package com.premisave.auth.service.security;

import com.premisave.auth.config.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Builds a ClientContext from a request and writes the device cookie on responses. */
@Component
public class ClientContextResolver {

    public static final String DEVICE_HEADER = "X-Device-Token";
    public static final String PLATFORM_HEADER = "X-Client-Platform";

    private static final int MAX_TOKEN_LENGTH = 128;

    private final ClientIpResolver ipResolver;
    private final SecurityProperties properties;

    public ClientContextResolver(ClientIpResolver ipResolver, SecurityProperties properties) {
        this.ipResolver = ipResolver;
        this.properties = properties;
    }

    public ClientContext resolve(HttpServletRequest request) {
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (properties.getDeviceCookieName().equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }
        if (token == null || token.isBlank()) {
            token = request.getHeader(DEVICE_HEADER);
        }
        if (token != null && (token.isBlank() || token.length() > MAX_TOKEN_LENGTH)) {
            token = null;
        }

        String platform = request.getHeader(PLATFORM_HEADER);
        boolean nativeApp = platform != null && platform.trim().equalsIgnoreCase("mobile");

        return new ClientContext(ipResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT), token, nativeApp);
    }

    /** Sets the HttpOnly device cookie. Called only when a new device token was issued. */
    public void writeDeviceCookie(HttpServletResponse response, String token) {
        if (token == null) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(properties.getDeviceCookieName(), token)
                .httpOnly(true)
                .secure(properties.isDeviceCookieSecure())
                .sameSite(properties.getDeviceCookieSameSite())
                .path("/")
                .maxAge(Duration.ofDays(properties.getDeviceCookieMaxAgeDays()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
