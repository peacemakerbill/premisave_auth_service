package com.premisave.auth.service.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.premisave.auth.dto.OAuthUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.premisave.auth.service.oauth.OAuthUtils.asString;
import static com.premisave.auth.service.oauth.OAuthUtils.isBlank;

/**
 * Google sign-in: verifies a Google ID token (signature, issuer, expiry and
 * audience) with Google's own verifier. The frontend obtains the ID token
 * from Google Identity Services (web) or google_sign_in (Flutter).
 *
 * oauth.google.client-id may hold several comma-separated client IDs
 * (for example web, Android and iOS); a token issued to any of them is accepted.
 * If it is unset, Google sign-in is refused with a clear message and the
 * service still starts.
 *
 * Apps that cannot run Google's own sign-in (Linux and Windows desktop) send an
 * authorization code and the redirect it was issued for instead. The code is
 * exchanged here with oauth.google.client-secret, and the ID token Google returns
 * is verified exactly like one sent directly. The client the code was issued to is
 * oauth.google.code-client-id, or the first client ID when that is unset; it must be
 * a Web application client that lists the redirect URI.
 */
@Slf4j
@Component
public class GoogleOAuthClient implements OAuthProviderClient {

    /** Google picture URLs end in a size suffix such as "=s96-c". */
    private static final Pattern SIZE_SUFFIX = Pattern.compile("=s\\d+(-c)?$");

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final GoogleIdTokenVerifier verifier;
    private final boolean configured;
    private final RestClient http;
    private final OAuthRedirectPolicy redirects;
    private final String clientSecret;
    private final String codeClientId;

    public GoogleOAuthClient(RestClient oauthRestClient,
                             OAuthRedirectPolicy redirects,
                             @Value("${oauth.google.client-id}") String clientIds,
                             @Value("${oauth.google.client-secret:}") String clientSecret,
                             @Value("${oauth.google.code-client-id:}") String codeClientId) {
        this.http = oauthRestClient;
        this.redirects = redirects;
        this.clientSecret = clientSecret.trim();

        List<String> listed = Arrays.stream(clientIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
        String chosen = codeClientId.trim();
        if (chosen.isEmpty() && !listed.isEmpty()) {
            chosen = listed.get(0);
        }
        this.codeClientId = chosen;

        // A token issued to the code client is accepted even when it is not in the main list
        List<String> audience = new ArrayList<>(listed);
        if (!chosen.isEmpty() && !audience.contains(chosen)) {
            audience.add(chosen);
        }
        this.configured = !audience.isEmpty();

        // Built once: the verifier caches Google's public signing keys. With an
        // empty audience every token fails verification, so an unconfigured
        // client can never accept a token by accident.
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(audience)
                .build();
    }

    @Override
    public String provider() {
        return "google";
    }

    @Override
    public OAuthUserInfo fetchUser(String credential, String redirectUri) {
        if (isBlank(redirectUri)) {
            return fetchUser(credential);
        }
        if (!configured) {
            throw new RuntimeException("Google sign-in is not configured on this server");
        }
        if (isBlank(credential)) {
            throw new RuntimeException("Google sign-in requires the authorization code in 'token'");
        }
        return fetchUser(exchangeCode(credential.trim(), redirects.require(redirectUri)));
    }

    /** Exchanges an authorization code for the ID token. The client secret never leaves this server. */
    private String exchangeCode(String code, String redirectUri) {
        if (clientSecret.isEmpty() || codeClientId.isEmpty()) {
            throw new RuntimeException("Google sign-in by authorization code is not configured on this server");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", codeClientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        Map<String, Object> body;
        try {
            body = http.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(MAP);
        } catch (RestClientException e) {
            log.warn("Google code exchange failed: {}", e.getMessage());
            throw new RuntimeException("Google sign-in failed: could not exchange the authorization code");
        }
        String idToken = body == null ? null : asString(body.get("id_token"));
        if (isBlank(idToken)) {
            throw new RuntimeException("Google sign-in failed: no ID token returned");
        }
        return idToken;
    }

    @Override
    public OAuthUserInfo fetchUser(String idTokenString) {
        if (!configured) {
            throw new RuntimeException("Google sign-in is not configured on this server");
        }
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