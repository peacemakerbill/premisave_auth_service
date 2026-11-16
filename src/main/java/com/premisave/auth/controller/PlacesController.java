package com.premisave.auth.controller;

import com.premisave.auth.dto.PlaceResponse;
import com.premisave.auth.dto.PlaceSuggestionResponse;
import com.premisave.auth.exception.ApiException;
import com.premisave.auth.service.places.GooglePlacesService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Address search for the apps. The Google key stays here. When no key is set, GET /places/status says so
 * and the apps use a free search instead.
 */
@RestController
@RequestMapping("/places")
@PreAuthorize("isAuthenticated()")
public class PlacesController {

    private static final Pattern COUNTRY = Pattern.compile("^[A-Za-z]{2}$");
    private static final Pattern PLACE_ID = Pattern.compile("^[A-Za-z0-9_-]{10,300}$");
    private static final Pattern SESSION = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final GooglePlacesService places;

    public PlacesController(GooglePlacesService places) {
        this.places = places;
    }

    /** Whether Google search is set up, so the app can choose its search without trying and failing. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", places.isEnabled());
        body.put("provider", "google");
        return ResponseEntity.ok(body);
    }

    /** Example: /places/suggest?q=donholm&country=KE&session=abc12345 */
    @GetMapping("/suggest")
    public ResponseEntity<List<PlaceSuggestionResponse>> suggest(
            @RequestParam String q,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String session) {
        String input = q.trim();
        if (input.length() < 2 || input.length() > 120) {
            throw ApiException.badRequest("Type between 2 and 120 characters to search.");
        }
        return ResponseEntity.ok(places.suggest(user(), input, country(country), session(session)));
    }

    /** Coordinates and address parts for a suggestion. */
    @GetMapping("/details")
    public ResponseEntity<PlaceResponse> details(
            @RequestParam String id,
            @RequestParam(required = false) String session) {
        if (!PLACE_ID.matcher(id).matches()) {
            throw ApiException.badRequest("That place id is not valid.");
        }
        return ResponseEntity.ok(places.details(user(), id, session(session)));
    }

    /** The address at a point on the map. Answers 404 when nothing is known there. */
    @GetMapping("/reverse")
    public ResponseEntity<PlaceResponse> reverse(@RequestParam double lat, @RequestParam double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw ApiException.badRequest("That position is not valid.");
        }
        PlaceResponse found = places.reverse(user(), lat, lng);
        if (found == null) {
            throw ApiException.notFound("No address is known at that position.");
        }
        return ResponseEntity.ok(found);
    }

    private static String country(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (!COUNTRY.matcher(raw.trim()).matches()) {
            throw ApiException.badRequest("Country must be a two letter code such as KE.");
        }
        return raw.trim().toUpperCase();
    }

    private static String session(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (!SESSION.matcher(raw.trim()).matches()) {
            throw ApiException.badRequest("The session value is not valid.");
        }
        return raw.trim();
    }

    private static String user() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
