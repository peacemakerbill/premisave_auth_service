package com.premisave.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.premisave.auth.dto.AuthResponse;
import com.premisave.auth.dto.OAuthRequest;
import com.premisave.auth.dto.OAuthUserInfo;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.Language;
import com.premisave.auth.enums.Role;
import com.premisave.auth.repository.UserRepository;
import com.premisave.auth.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class OAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RestTemplate restTemplate;

    @Value("${oauth.google.client-id}")
    private String googleClientId;

    public OAuthService(UserRepository userRepository,
                        JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.restTemplate = new RestTemplate();
    }

    // ─────────────────────────────────────────────────────────────
    //  Entry point — routes to the correct provider verifier
    // ─────────────────────────────────────────────────────────────

    public AuthResponse handleOAuth(OAuthRequest request) {
        String provider = request.getProvider().toLowerCase().trim();

        OAuthUserInfo userInfo = switch (provider) {
            case "google"   -> verifyGoogleToken(request.getToken());
            case "facebook" -> verifyFacebookToken(request.getToken());
            default -> throw new RuntimeException("Unsupported OAuth provider: " + provider);
        };

        User user = findOrCreateUser(userInfo);

        AuthResponse response = new AuthResponse();
        response.setToken(jwtService.generateToken(user));
        response.setRole(user.getRole().name());
        return response;
    }

    // ─────────────────────────────────────────────────────────────
    //  Google — verify ID token using Google's library
    // ─────────────────────────────────────────────────────────────

    private OAuthUserInfo verifyGoogleToken(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new RuntimeException("Invalid Google token — verification failed");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            return OAuthUserInfo.builder()
                    .providerId(payload.getSubject())
                    .email(payload.getEmail())
                    .firstName((String) payload.get("given_name"))
                    .lastName((String) payload.get("family_name"))
                    .profilePictureUrl((String) payload.get("picture"))
                    .provider("google")
                    .build();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Google token verification error: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Facebook — exchange access token against Graph API
    // ─────────────────────────────────────────────────────────────

    @SuppressWarnings("rawtypes")
	private OAuthUserInfo verifyFacebookToken(String accessToken) {
        try {
            String url = "https://graph.facebook.com/me"
                    + "?fields=id,first_name,last_name,email,picture.type(large)"
                    + "&access_token=" + accessToken;

            ResponseEntity<Map> responseEntity = restTemplate.getForEntity(url, Map.class);
            Map<?, ?> body = responseEntity.getBody();

            if (body == null || body.containsKey("error")) {
                throw new RuntimeException("Invalid Facebook token — Graph API rejected it");
            }

            String email = (String) body.get("email");
            if (email == null || email.isBlank()) {
                // Facebook can withhold email if user hasn't granted permission
                throw new RuntimeException(
                        "Facebook did not return an email address. "
                        + "Please ensure the 'email' permission is granted in your Facebook app.");
            }

            // Extract nested picture URL safely
            String pictureUrl = null;
            Object pictureObj = body.get("picture");
            if (pictureObj instanceof Map<?, ?> picMap) {
                Object dataObj = picMap.get("data");
                if (dataObj instanceof Map<?, ?> dataMap) {
                    pictureUrl = (String) dataMap.get("url");
                }
            }

            return OAuthUserInfo.builder()
                    .providerId((String) body.get("id"))
                    .email(email)
                    .firstName((String) body.get("first_name"))
                    .lastName((String) body.get("last_name"))
                    .profilePictureUrl(pictureUrl)
                    .provider("facebook")
                    .build();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Facebook token verification error: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Upsert: find existing user by email, or create a new one
    // ─────────────────────────────────────────────────────────────

    private User findOrCreateUser(OAuthUserInfo info) {
        Optional<User> existing = userRepository.findByEmail(info.getEmail());

        if (existing.isPresent()) {
            User user = existing.get();

            // Keep profile picture in sync if user doesn't have one
            if (user.getProfilePictureUrl() == null
                    && info.getProfilePictureUrl() != null) {
                user.setProfilePictureUrl(info.getProfilePictureUrl());
                userRepository.save(user);
            }

            if (!user.isActive()) {
                throw new RuntimeException("Account is deactivated. Please contact support.");
            }

            // If account exists but was registered with email/password,
            // we still let them in — the email match is authoritative.
            return user;
        }

        // ── New user — auto-create and sign in ──
        User user = new User();
        user.setEmail(info.getEmail());
        user.setFirstName(info.getFirstName());
        user.setLastName(info.getLastName());
        user.setProfilePictureUrl(info.getProfilePictureUrl());
        user.setRole(Role.CLIENT);
        user.setActive(true);
        user.setVerified(true);   // OAuth provider already verified the email
        user.setArchived(false);
        user.setLanguage(Language.ENGLISH);

        // Generate a unique username from the email prefix
        String baseUsername = info.getEmail().split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
        user.setUsername(resolveUniqueUsername(baseUsername));

        // OAuth users have no password — set a random unguessable one
        user.setPassword(UUID.randomUUID().toString());

        user = userRepository.save(user);
        log.info("New OAuth user created: {} via {}", user.getEmail(), info.getProvider());
        return user;
    }

    /**
     * Appends a numeric suffix if the desired username is already taken.
     * e.g. "johndoe" → "johndoe2" → "johndoe3"
     */
    private String resolveUniqueUsername(String base) {
        if (!userRepository.existsByUsername(base)) return base;
        int suffix = 2;
        while (userRepository.existsByUsername(base + suffix)) suffix++;
        return base + suffix;
    }
}