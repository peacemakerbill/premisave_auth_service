package com.premisave.auth.security;

import com.premisave.auth.entity.User;
import com.premisave.auth.enums.Role;
import com.premisave.auth.service.security.RequestAttributes;
import com.premisave.auth.service.security.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SessionService sessionService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserDetailsServiceImpl userDetailsService,
                                   RedisTemplate<String, Object> redisTemplate,
                                   SessionService sessionService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        // Check if token is blacklisted (for logout)
        if (isTokenBlacklisted(jwt)) {
            reject(response, "Token has been revoked. Please login again.");
            return;
        }

        // Malformed/garbage tokens (wrong signature, not a JWT at all, expired) are treated as
        // "not authenticated": the request continues without a user and Spring Security's own
        // rules answer 401/403 for protected endpoints.
        String userEmail = null;
        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            log.warn("Rejected malformed/invalid JWT: {}", e.getMessage());
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(userEmail);
            } catch (UsernameNotFoundException e) {
                filterChain.doFilter(request, response);
                return;
            }

            if (jwtService.isTokenValid(jwt, userDetails)) {
                // A refresh token must never work as an access token.
                if (JwtService.REFRESH.equals(safeTokenType(jwt))) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // Privileged access needs a verified, active account, whatever the token says.
                if (userDetails instanceof User account && account.getRole() == Role.ADMIN
                        && !(account.isVerified() && account.isActive())) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // Tokens issued by a session must belong to a session that is still active.
                String sessionId = safeSessionId(jwt);
                if (sessionId != null) {
                    String userId = userDetails instanceof User account ? account.getId() : null;
                    if (!sessionService.isActive(sessionId, userId)) {
                        reject(response, "Your session has ended. Please sign in again.");
                        return;
                    }
                    request.setAttribute(RequestAttributes.SESSION_ID, sessionId);
                    sessionService.touch(sessionId);
                }

                UsernamePasswordAuthenticationToken authToken = 
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String safeTokenType(String jwt) {
        try {
            return jwtService.extractTokenType(jwt);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String safeSessionId(String jwt) {
        try {
            return jwtService.extractSessionId(jwt);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // Same shape as every other error the API returns
        response.getWriter().write("{\"timestamp\":\"" + java.time.LocalDateTime.now() + "\","
                + "\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + jsonEscape(message) + "\"}");
    }

    private static String jsonEscape(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : (text == null ? "" : text).toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Check if token has been blacklisted (logged out)
     */
    private boolean isTokenBlacklisted(String jwt) {
        try {
            Boolean isBlacklisted = (Boolean) redisTemplate.opsForValue().get("blacklist:" + jwt);
            return Boolean.TRUE.equals(isBlacklisted);
        } catch (Exception e) {
            // Fail open - better to allow access than block on Redis failure
            return false;
        }
    }
}