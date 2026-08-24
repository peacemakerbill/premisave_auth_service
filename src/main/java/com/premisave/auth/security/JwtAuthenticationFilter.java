package com.premisave.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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

    public JwtAuthenticationFilter(JwtService jwtService, 
                                   UserDetailsServiceImpl userDetailsService,
                                   RedisTemplate<String, Object> redisTemplate) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
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
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Token has been revoked. Please login again.\"}");
            return;
        }

        // Malformed/garbage tokens (wrong signature, not a JWT at all, etc.)
        // throw here - catch and treat as "not authenticated" rather than
        // letting it crash the request with a raw 500. This is a normal,
        // expected condition (any client can send a bad token), not a bug,
        // so it's handled the same way a missing Authorization header is
        // handled above: let the request continue unauthenticated, and
        // Spring Security's own authorization rules return 401/403 for
        // any protected endpoint naturally.
        String userEmail = null;
        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            log.warn("Rejected malformed/invalid JWT: {}", e.getMessage());
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = 
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
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