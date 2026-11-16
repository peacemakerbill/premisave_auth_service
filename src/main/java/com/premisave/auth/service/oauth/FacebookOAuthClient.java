package com.premisave.auth.service.oauth;

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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static com.premisave.auth.service.oauth.OAuthUtils.asMap;
import static com.premisave.auth.service.oauth.OAuthUtils.asString;
import static com.premisave.auth.service.oauth.OAuthUtils.isBlank;

/**
 * Facebook sign-in: the frontend sends the user access token from the
 * Facebook Login SDK (permissions: public_profile, email).
 *
 * The token is first checked with /debug_token to prove it is valid AND was
 * issued to this app — without that check, a token issued to any other
 * Facebook app could be replayed here to sign in as its owner. The profile
 * is then read with an appsecret_proof.
 *
 * Apps that cannot run the Facebook SDK (Linux desktop) send an authorization code
 * and the redirect it was issued for instead. The code is exchanged here with the app
 * secret, and the access token that comes back goes through the same checks.
 *
 * If the app id or secret is unset, Facebook sign-in is refused with a clear
 * message and the service still starts.
 */
@Slf4j
@Component
public class FacebookOAuthClient implements OAuthProviderClient {

    private static final String GRAPH = "https://graph.facebook.com";
    private static final String FIELDS = "id,first_name,last_name,name,email,picture.width(400).height(400)";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient http;
    private final String appId;
    private final String appSecret;
    private final OAuthRedirectPolicy redirects;

    public FacebookOAuthClient(RestClient oauthRestClient,
                               OAuthRedirectPolicy redirects,
                               @Value("${oauth.facebook.app-id}") String appId,
                               @Value("${oauth.facebook.app-secret}") String appSecret) {
        this.http = oauthRestClient;
        this.redirects = redirects;
        this.appId = appId.trim();
        this.appSecret = appSecret.trim();
    }

    private boolean configured() {
        return !appId.isEmpty() && !appSecret.isEmpty();
    }

    @Override
    public String provider() {
        return "facebook";
    }

    @Override
    public OAuthUserInfo fetchUser(String credential, String redirectUri) {
        if (isBlank(redirectUri)) {
            return fetchUser(credential);
        }
        if (!configured()) {
            throw new RuntimeException("Facebook sign-in is not configured on this server");
        }
        if (isBlank(credential)) {
            throw new RuntimeException("Facebook sign-in requires the authorization code in 'token'");
        }
        return fetchUser(exchangeCode(credential.trim(), redirects.require(redirectUri)));
    }

    /** Exchanges an authorization code for a user access token. The app secret never leaves this server. */
    private String exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", appId);
        form.add("client_secret", appSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        Map<String, Object> body;
        try {
            body = http.post()
                    .uri(GRAPH + "/oauth/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(MAP);
        } catch (RestClientException e) {
            log.warn("Facebook code exchange failed: {}", e.getMessage());
            throw new RuntimeException("Facebook sign-in failed: could not exchange the authorization code");
        }
        String token = body == null ? null : asString(body.get("access_token"));
        if (isBlank(token)) {
            throw new RuntimeException("Facebook sign-in failed: no access token returned");
        }
        return token;
    }

    @Override
    public OAuthUserInfo fetchUser(String accessToken) {
        if (!configured()) {
            throw new RuntimeException("Facebook sign-in is not configured on this server");
        }
        if (isBlank(accessToken)) {
            throw new RuntimeException("Facebook sign-in requires the Facebook access token in 'token'");
        }
        accessToken = accessToken.trim();

        String tokenUserId = verifyTokenForThisApp(accessToken);

        Map<String, Object> me;
        try {
            me = http.get()
                    .uri(GRAPH + "/me?fields={fields}&access_token={token}&appsecret_proof={proof}",
                            FIELDS, accessToken, appSecretProof(accessToken))
                    .retrieve()
                    .body(MAP);
        } catch (RestClientException e) {
            log.warn("Facebook /me request failed: {}", e.getMessage());
            throw new RuntimeException("Facebook rejected the access token");
        }
        if (me == null) {
            throw new RuntimeException("Facebook returned an empty profile");
        }

        String id = asString(me.get("id"));
        if (!tokenUserId.equals(id)) {
            throw new RuntimeException("Facebook token does not belong to this user");
        }

        String email = asString(me.get("email"));
        if (isBlank(email)) {
            throw new RuntimeException("Facebook did not return an email address. "
                    + "Grant the 'email' permission and make sure the Facebook account has a confirmed email.");
        }

        // Skip Facebook's default silhouette avatar
        String pictureUrl = null;
        Map<String, Object> pictureData = asMap(asMap(me.get("picture")).get("data"));
        if (!Boolean.TRUE.equals(pictureData.get("is_silhouette"))) {
            pictureUrl = asString(pictureData.get("url"));
        }

        return OAuthUserInfo.builder()
                .provider(provider())
                .providerId(id)
                .email(email)
                .firstName(asString(me.get("first_name")))
                .lastName(asString(me.get("last_name")))
                .fullName(asString(me.get("name")))
                .profilePictureUrl(pictureUrl)
                .build();
    }

    /** Returns the Facebook user id the token belongs to, or throws if the token is invalid or for another app. */
    private String verifyTokenForThisApp(String accessToken) {
        Map<String, Object> body;
        try {
            body = http.get()
                    .uri(GRAPH + "/debug_token?input_token={input}&access_token={appToken}",
                            accessToken, appId + "|" + appSecret)
                    .retrieve()
                    .body(MAP);
        } catch (RestClientException e) {
            log.warn("Facebook debug_token request failed: {}", e.getMessage());
            throw new RuntimeException("Could not verify the Facebook token");
        }

        Map<String, Object> data = asMap(body == null ? null : body.get("data"));
        boolean valid = Boolean.TRUE.equals(data.get("is_valid"));
        String tokenAppId = asString(data.get("app_id"));
        String userId = asString(data.get("user_id"));

        if (!valid) {
            throw new RuntimeException("Invalid or expired Facebook token");
        }
        if (!appId.equals(tokenAppId)) {
            throw new RuntimeException("Facebook token was not issued for this app");
        }
        if (isBlank(userId)) {
            throw new RuntimeException("Facebook token is not a user access token");
        }
        return userId;
    }

    /** HMAC-SHA256 of the access token keyed with the app secret, hex encoded. */
    private String appSecretProof(String accessToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute Facebook appsecret_proof", e);
        }
    }
}