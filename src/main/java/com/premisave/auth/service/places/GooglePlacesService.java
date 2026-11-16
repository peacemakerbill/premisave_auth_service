package com.premisave.auth.service.places;

import com.premisave.auth.dto.PlaceResponse;
import com.premisave.auth.dto.PlaceSuggestionResponse;
import com.premisave.auth.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Address search and map pin lookups through Google Places (New) and the Google Geocoding API.
 * The key stays on the server: the apps call /places/** on this service, never Google directly.
 * Nothing here runs until GOOGLE_MAPS_API_KEY is set, and the apps then fall back to a free search.
 *
 * Results are not stored. Google's terms limit how long place data may be kept.
 */
@Slf4j
@Service
public class GooglePlacesService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final String AUTOCOMPLETE_URL = "https://places.googleapis.com/v1/places:autocomplete";
    private static final String DETAILS_URL = "https://places.googleapis.com/v1/places/";
    private static final String GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    private static final String KEY_HEADER = "X-Goog-Api-Key";
    private static final String MASK_HEADER = "X-Goog-FieldMask";

    private final String apiKey;
    private final int perUserPerMinute;
    private final RestClient http;

    /** Fixed one minute windows per user: {window start millis, calls in window}. */
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    public GooglePlacesService(
            @Value("${places.google-api-key:}") String apiKey,
            @Value("${places.timeout-ms:4000}") int timeoutMs,
            @Value("${places.requests-per-user-per-minute:90}") int perUserPerMinute) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.perUserPerMinute = Math.max(10, perUserPerMinute);
        Duration timeout = Duration.ofMillis(Math.max(500, timeoutMs));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.http = RestClient.builder().requestFactory(factory).build();
        if (this.apiKey.isEmpty()) {
            log.info("Places search is off: GOOGLE_MAPS_API_KEY is not set, so the apps use the free OpenStreetMap search");
        }
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    // ------------------------------------------------------------------ suggestions

    /** Suggestions for what the user has typed so far, best first. */
    public List<PlaceSuggestionResponse> suggest(String user, String input, String countryCode, String session) {
        guard(user);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", input);
        body.put("languageCode", "en");
        if (countryCode != null) {
            body.put("includedRegionCodes", List.of(countryCode.toLowerCase()));
        }
        if (session != null) {
            body.put("sessionToken", session);
        }
        Map<String, Object> res = call(() -> http.post()
                .uri(AUTOCOMPLETE_URL)
                .header(KEY_HEADER, apiKey)
                .header(MASK_HEADER, "suggestions.placePrediction.placeId,suggestions.placePrediction.text,"
                        + "suggestions.placePrediction.structuredFormat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP));

        List<PlaceSuggestionResponse> out = new ArrayList<>();
        for (Object s : list(res == null ? null : res.get("suggestions"))) {
            Map<String, Object> prediction = map(map(s).get("placePrediction"));
            String id = text(prediction.get("placeId"));
            if (id == null) {
                continue;
            }
            Map<String, Object> format = map(prediction.get("structuredFormat"));
            String main = text(map(format.get("mainText")).get("text"));
            String secondary = text(map(format.get("secondaryText")).get("text"));
            if (main == null) {
                main = text(map(prediction.get("text")).get("text"));
            }
            if (main != null) {
                out.add(new PlaceSuggestionResponse(id, main, secondary == null ? "" : secondary));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ details

    /** The coordinates and address parts of a suggestion. Passing the same session ends it for billing. */
    public PlaceResponse details(String user, String placeId, String session) {
        guard(user);
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(DETAILS_URL + placeId).queryParam("languageCode", "en");
        if (session != null) {
            uri.queryParam("sessionToken", session);
        }
        URI target = uri.build().toUri();
        Map<String, Object> res = call(() -> http.get()
                .uri(target)
                .header(KEY_HEADER, apiKey)
                .header(MASK_HEADER, "id,displayName,location,addressComponents,types")
                .retrieve()
                .body(MAP));
        if (res == null) {
            throw ApiException.notFound("That place could not be found.");
        }
        Map<String, Object> location = map(res.get("location"));
        Double lat = number(location.get("latitude"));
        Double lng = number(location.get("longitude"));
        if (lat == null || lng == null) {
            throw ApiException.notFound("That place has no location.");
        }
        List<Component> components = new ArrayList<>();
        for (Object c : list(res.get("addressComponents"))) {
            Map<String, Object> m = map(c);
            components.add(new Component(text(m.get("longText")), text(m.get("shortText")), strings(m.get("types"))));
        }
        return toPlace(lat, lng, text(map(res.get("displayName")).get("text")), strings(res.get("types")), components);
    }

    // ------------------------------------------------------------------ reverse

    /** The address at a point on the map, or null when Google knows nothing there. */
    public PlaceResponse reverse(String user, double latitude, double longitude) {
        guard(user);
        URI target = UriComponentsBuilder.fromUriString(GEOCODE_URL)
                .queryParam("latlng", latitude + "," + longitude)
                .queryParam("language", "en")
                .queryParam("result_type", "street_address|route|neighborhood|sublocality|locality")
                .queryParam("key", apiKey)
                .build().encode().toUri();
        Map<String, Object> res = call(() -> http.get().uri(target).retrieve().body(MAP));
        if (res == null) {
            return null;
        }
        String status = text(res.get("status"));
        if ("ZERO_RESULTS".equals(status)) {
            return null;
        }
        if (!"OK".equals(status)) {
            log.warn("Google Geocoding answered {}: {}", status, text(res.get("error_message")));
            throw ApiException.unavailable("Address lookup is not available right now.");
        }
        List<Object> results = list(res.get("results"));
        if (results.isEmpty()) {
            return null;
        }
        Map<String, Object> first = map(results.get(0));
        List<Component> components = new ArrayList<>();
        for (Object c : list(first.get("address_components"))) {
            Map<String, Object> m = map(c);
            components.add(new Component(text(m.get("long_name")), text(m.get("short_name")), strings(m.get("types"))));
        }
        List<String> types = strings(first.get("types"));
        // The most specific component names a neighbourhood or town when the pin is not on a street.
        String name = components.isEmpty() ? null : components.get(0).longName();
        return toPlace(latitude, longitude, name, types, components);
    }

    // ------------------------------------------------------------------ mapping

    private record Component(String longName, String shortName, List<String> types) {
        boolean is(String type) {
            return types.contains(type);
        }
    }

    private static PlaceResponse toPlace(double lat, double lng, String displayName, List<String> types, List<Component> components) {
        String houseNumber = pick(components, false, "street_number");
        String street = pick(components, false, "route");
        String district = pick(components, false, "sublocality_level_1", "sublocality", "neighborhood");
        String city = pick(components, false, "locality", "postal_town", "administrative_area_level_2");
        String state = pick(components, false, "administrative_area_level_1");
        String postcode = pick(components, false, "postal_code");
        String country = pick(components, false, "country");
        String countryCode = pick(components, true, "country");

        boolean addressLike = types.contains("route") || types.contains("street_address") || types.contains("premise")
                || types.contains("postal_code") || types.contains("plus_code");
        String name = addressLike ? null : displayName;
        if (types.contains("route") && street == null) {
            street = displayName;
        }
        return new PlaceResponse(lat, lng, name, houseNumber, street, district, city, state, postcode, country,
                countryCode == null ? null : countryCode.toUpperCase());
    }

    /** The first component of the first matching type, in the order the types are listed. */
    private static String pick(List<Component> components, boolean shortForm, String... types) {
        for (String type : types) {
            for (Component c : components) {
                if (c.is(type)) {
                    String v = shortForm ? c.shortName() : c.longName();
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ plumbing

    private <T> T call(java.util.function.Supplier<T> request) {
        if (!isEnabled()) {
            throw ApiException.unavailable("Address search by Google is not set up.");
        }
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            log.warn("Google Places answered {}: {}", e.getStatusCode().value(), abbreviate(e.getResponseBodyAsString()));
            throw ApiException.unavailable("Address search is not available right now.");
        } catch (RestClientException e) {
            log.warn("Google Places could not be reached: {}", e.getMessage());
            throw ApiException.unavailable("Address search is not available right now.");
        }
    }

    /** Stops one account from running up the Google bill. */
    private void guard(String user) {
        if (!isEnabled()) {
            throw ApiException.unavailable("Address search by Google is not set up.");
        }
        long now = System.currentTimeMillis();
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue()[0] > 60_000);
        }
        long[] w = windows.compute(user == null ? "anonymous" : user, (k, old) -> {
            if (old == null || now - old[0] > 60_000) {
                return new long[] {now, 1};
            }
            old[1]++;
            return old;
        });
        if (w[1] > perUserPerMinute) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many address searches. Wait a moment and try again.");
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 300 ? t.substring(0, 300) + "..." : t;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        for (Object v : list(o)) {
            if (v instanceof String s) {
                out.add(s);
            }
        }
        return out;
    }

    private static String text(Object o) {
        if (o instanceof String s) {
            String t = s.trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }

    private static Double number(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }
}
