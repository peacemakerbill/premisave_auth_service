package com.premisave.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import com.premisave.auth.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    public static final String ACCESS = "access";
    public static final String REFRESH = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * Lifetime of session-bound access tokens in minutes. 0 keeps jwt.expiration, so existing clients
     * behave as before. A small value (15 to 60) makes a revoked session stop working everywhere within
     * that time, including in services that only check the signature. Clients then have to use the
     * refresh token (POST /auth/refresh) to stay signed in.
     */
    @Value("${app.security.access-token-minutes:0}")
    private long accessTokenMinutes;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("roles", user.getRole() != null ? user.getRole().name() : "CLIENT");
        claims.put("email", user.getEmail());
        return generateToken(claims, user, expiration);
    }

    /**
     * Access token bound to a server-side session. The "sid" claim lets this service end the token
     * at any time by revoking the session. Other services that only verify the signature ignore the
     * claim, so set app.security.access-token-minutes to a short value or have them call /internal/sessions/{sid}.
     */
    public String generateAccessToken(User user, String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("roles", user.getRole() != null ? user.getRole().name() : "CLIENT");
        claims.put("email", user.getEmail());
        claims.put("sid", sessionId);
        claims.put("tokenType", ACCESS);
        return generateToken(claims, user, accessTokenMillis());
    }

    /** Lifetime of a session-bound access token, in milliseconds. */
    public long accessTokenMillis() {
        return accessTokenMinutes > 0 ? accessTokenMinutes * 60_000L : expiration;
    }

    /** Same lifetime in seconds, for clients that schedule their next refresh. */
    public long accessTokenSeconds() {
        return accessTokenMillis() / 1000;
    }

    /** Refresh token for a session. "gen" must match the session's current generation, so a replayed token is detectable. */
    public String generateRefreshToken(User user, String sessionId, int generation, Instant expiresAt) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("sid", sessionId);
        claims.put("gen", generation);
        claims.put("tokenType", REFRESH);
        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(Date.from(expiresAt))
                .signWith(getSignInKey())
                .compact();
    }

    /** Session id claim, or null for tokens issued before sessions existed. */
    public String extractSessionId(String token) {
        return extractClaim(token, claims -> claims.get("sid", String.class));
    }

    /** "access", "refresh", or null for older tokens. */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("tokenType", String.class));
    }

    public Integer extractGeneration(String token) {
        return extractClaim(token, claims -> claims.get("gen", Integer.class));
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails, long expirationTime) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSignInKey())
                .compact();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails, expiration * 30);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    /**
     * Truncate/pad to 32 bytes — booking-service mirrors this exactly
     * so tokens are cross-verifiable between services.
     */
    private SecretKey getSignInKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);

            if (keyBytes.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
                keyBytes = padded;
            } else if (keyBytes.length > 32) {
                byte[] truncated = new byte[32];
                System.arraycopy(keyBytes, 0, truncated, 0, 32);
                keyBytes = truncated;
            }

            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            return Keys.hmacShaKeyFor(secret.getBytes());
        }
    }
}