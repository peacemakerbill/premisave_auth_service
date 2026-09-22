package com.premisave.auth.service.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.premisave.auth.dto.OAuthRequest;
import com.premisave.auth.dto.OAuthUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.premisave.auth.service.oauth.OAuthUtils.asString;
import static com.premisave.auth.service.oauth.OAuthUtils.isBlank;

/**
 * Google sign-in: verifies a Google ID token (signature, issuer, expiry and
 * audience) with Google's own verifier. The frontend obtains the ID token
 * from Google Identity Services (web) or google_sign_in (Flutter).
 *
 * oauth.google.client-id may hold several comma-separated client IDs
 * (for example web, Android and iOS) — a token issued to any of them is accepted.
 */
@Slf4j
@Component
public class GoogleOAuthClient implements OAuthProviderClient {

    /** Google picture URLs end in a size suffix such as "=s96-c". */
    private static final Pattern SIZE_SUFFIX = Pattern.compile("=s\\d+(-c)?$");

    private final GoogleIdTokenVerifier verifier;

    public GoogleOAuthClient(@Value("${oauth.google.client-id}") String clientIds) {
        List<String> audience = Arrays.stream(clientIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();

        // Built once: the verifier caches Google's public signing keys.
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(audience)
                .build();
    }

    @Override
    public String provider() {
        return "google";
    }

    @Override
    public OAuthUserInfo fetchUser(OAuthRequest request) {
        String idTokenString = request.getToken();
        if (isBlank(idTokenString)) {
            throw new RuntimeException("Google sign-in requires the Google ID token in 'token'");
        }

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString.trim());
        } catch (Exception e) {
            log.warn("Google ID token verification error: {}", e.getMessage());
            throw new RuntimeException("Invalid Google token");
        }
        if (idToken == null) {
            throw new RuntimeException("Invalid Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();

        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new RuntimeException("Your Google account email is not verified");
        }

        return OAuthUserInfo.builder()
                .provider(provider())
                .providerId(payload.getSubject())
                .email(payload.getEmail())
                .firstName(asString(payload.get("given_name")))
                .lastName(asString(payload.get("family_name")))
                .fullName(asString(payload.get("name")))
                .profilePictureUrl(largePicture(asString(payload.get("picture"))))
                .build();
    }

    /** Requests a 400px version instead of Google's default 96px thumbnail. */
    private String largePicture(String url) {
        if (isBlank(url)) {
            return null;
        }
        return SIZE_SUFFIX.matcher(url).find()
                ? SIZE_SUFFIX.matcher(url).replaceFirst("=s400-c")
                : url;
    }
}