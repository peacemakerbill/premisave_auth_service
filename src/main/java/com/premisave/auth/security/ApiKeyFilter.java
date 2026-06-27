package com.premisave.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Protects designated endpoints with a shared API key.
 *
 * Clients must include the header:
 *   X-API-Key: <value of app.api-key in application.properties>
 *
 * Requests missing or supplying the wrong key receive 401 Unauthorized
 * with a plain JSON error body — no stack trace, no internal detail.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    /** Endpoints guarded by this filter (prefix-matched) */
    private static final String[] PROTECTED_PATHS = {
            "/profile/public/directory",
            "/internal/"
    };

    @Value("${app.api-key}")
    private String validApiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        for (String protected_path : PROTECTED_PATHS) {
            if (path.startsWith(protected_path)) {
                return false; // DO filter this path
            }
        }
        return true; // skip all other paths
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String providedKey = request.getHeader(API_KEY_HEADER);

        if (providedKey == null || !providedKey.equals(validApiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"error": "Unauthorized", "message": "Missing or invalid API key"}
                    """);
            return; // stop — do not call filterChain
        }

        filterChain.doFilter(request, response);
    }
}