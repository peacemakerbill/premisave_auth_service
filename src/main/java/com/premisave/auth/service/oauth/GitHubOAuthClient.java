package com.premisave.auth.service.oauth;

import com.premisave.auth.dto.OAuthRequest;
import com.premisave.auth.dto.OAuthUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

import static com.premisave.auth.service.oauth.OAuthUtils.asString;
import static com.premisave.auth.service.oauth.OAuthUtils.isBlank;

/**
 * GitHub sign-in (GitHub OAuth App).
 *
 * Preferred flow: the frontend redirects the user to
 *   https://github.com/login/oauth/authorize?client_id=...&redirect_uri=...&scope=user:email&state=...
 * and sends the returned "code" plus the same "redirectUri" to POST /auth/oauth.
 * The code is exchanged here, so the client secret never leaves the backend.
 *
 * Alternative: an access token in "token". It is only accepted after GitHub
 * confirms (via the check-token API) that it was issued to this app.
 *
 * oauth.github.client-id / client-secret are optional: if either is unset,
 * this client reports itself as unconfigured and GitHub sign-in is
 * refused with a clear message rather than the service failing to start.
 */
@Slf4j
@Component
public class GitHubOAuthClient implements OAuthProviderClient {

    private static final String API = "https://api.github.com";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String API_VERSION = "2022-11-28";
    private static final String USER_AGENT = "Premisave-Auth-Service";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {};

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;

    public GitHubOAuthClient(RestClient oauthRestClient,
                             @Value("${oauth.github.client-id}") String clientId,
                             @Value("${oauth.github.client-secret}") String clientSecret) {
        this.http = oauthRestClient;
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
    }

    private boolean configured() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    @Override
    public String provider() {
        return "github";
    }

    @Override
    public OAuthUserInfo fetchUser(OAuthRequest request) {
        if (!configured()) {
            throw new RuntimeException("GitHub sign-in is not configured on this server");
        }

        String accessToken;
        if (!isBlank(request.getCode())) {
            accessToken = exchangeCode(request.getCode().trim(), request.getRedirectUri());
        } else if (!isBlank(request.getToken())) {
            accessToken = request.getToken().trim();
            verifyTokenForThisApp(accessToken);
        } else {
            throw new RuntimeException("GitHub sign-in requires 'code' (with 'redirectUri') or an access token in 'token'");
        }

        Map<String, Object> user;
        try {
            user = http.get()
                    .uri(API + "/user")
                    .headers(h -> apiHeaders(h, accessToken))
                    .retrieve()
                    .body(MAP);
        } catch (RestClientException e) {
            log.warn("GitHub /user request failed: {}", e.getMessage());
            throw new RuntimeException("GitHub rejected the access token");
        }
        if (user == null) {
            throw new RuntimeException("GitHub returned an empty profile");
        }

        Object rawId = user.get("id");
        String id = rawId instanceof Number number ? String.valueOf(number.longValue()) : asString(rawId);
        if (isBlank(id)) {
            throw new RuntimeException("GitHub did not return a user id");
        }

        String email = verifiedEmail(accessToken, asString(user.get("email")));

        return OAuthUserInfo.builder()
                .provider(provider())
                .providerId(id)
                .email(email)
                .fullName(asString(user.get("name")))
                .usernameHint(asString(user.get("login")))
                .profilePictureUrl(largeAvatar(asString(user.get("avatar_url"))))
                .build();
    }

    /** Exchanges an authorization code for an access token. */
    private String exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        if (!isBlank(redirectUri)) {
            form.add("redirect_uri", redirectUri.trim());
        }

        Map<String, Object> body;
        try {
            body = http.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .body(form)
                    .retrieve()
                    .body(MAP);
        } catch (RestClientException e) {
            log.warn("GitHub code exchange failed: {}", e.getMessage());
            throw new RuntimeException("GitHub sign-in failed: could not exchange the authorization code");
        }
        if (body == null) {
            throw new RuntimeException("GitHub sign-in failed: empty token response");
        }

        // GitHub reports errors with HTTP 200 and an "error" field
        if (body.get("error") != null) {
            String description = asString(body.get("error_description"));
            throw new RuntimeException("GitHub sign-in failed: "
                    + (isBlank(description) ? asString(body.get("error")) : description));
        }

        String accessToken = asString(body.get("access_token"));
        if (isBlank(accessToken)) {
            throw new RuntimeException("GitHub sign-in failed: no access token returned");
        }
        return accessToken;
    }

    /** Confirms with GitHub that a client-supplied token was issued to this OAuth app. */
    private void verifyTokenForThisApp(String accessToken) {
        try {
            http.post()
                    .uri(API + "/applications/{clientId}/token", clientId)
                    .headers(h -> {
                        h.setBasicAuth(clientId, clientSecret);
                        h.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
                        h.set("X-GitHub-Api-Version", API_VERSION);
                        h.set(HttpHeaders.USER_AGENT, USER_AGENT);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("access_token", accessToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("GitHub token check failed: {}", e.getMessage());
            throw new RuntimeException("GitHub token is invalid or was not issued for this app");
        }
    }

    /**
     * Primary verified email, else any verified email (needs the user:email scope).
     * Falls back to the public profile email, which GitHub only allows to be a verified address.
     */
    private String verifiedEmail(String accessToken, String publicEmail) {
        try {
            List<Map<String, Object>> emails = http.get()
                    .uri(API + "/user/emails")
                    .headers(h -> apiHeaders(h, accessToken))
                    .retrieve()
                    .body(LIST);

            if (emails != null) {
                String primaryVerified = emails.stream()
                        .filter(e -> Boolean.TRUE.equals(e.get("verified")) && Boolean.TRUE.equals(e.get("primary")))
                        .map(e -> asString(e.get("email")))
                        .filter(e -> !isBlank(e))
                        .findFirst()
                        .orElse(null);
                if (primaryVerified != null) {
                    return primaryVerified;
                }

                String anyVerified = emails.stream()
                        .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                        .map(e -> asString(e.get("email")))
                        .filter(e -> !isBlank(e))
                        .findFirst()
                        .orElse(null);
                if (anyVerified != null) {
                    return anyVerified;
                }
            }
        } catch (RestClientResponseException e) {
            log.warn("Could not read GitHub emails (HTTP {}). Is the user:email scope granted?",
                    e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("GitHub /user/emails request failed: {}", e.getMessage());
        }

        if (!isBlank(publicEmail)) {
            return publicEmail;
        }
        throw new RuntimeException("GitHub did not return a verified email. "
                + "Request the 'user:email' scope and make sure the GitHub account has a verified email.");
    }

    private void apiHeaders(HttpHeaders headers, String accessToken) {
        headers.setBearerAuth(accessToken);
        headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", API_VERSION);
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
    }

    /** Requests a 400px avatar instead of GitHub's default size. */
    private String largeAvatar(String url) {
        if (isBlank(url)) {
            return null;
        }
        return url + (url.contains("?") ? "&" : "?") + "s=400";
    }
}