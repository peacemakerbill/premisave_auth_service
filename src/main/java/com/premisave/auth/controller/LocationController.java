package com.premisave.auth.controller;

import com.premisave.auth.dto.LocationResponse;
import com.premisave.auth.dto.LocationUpdateRequest;
import com.premisave.auth.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/location")
@PreAuthorize("isAuthenticated()")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PutMapping
    public ResponseEntity<LocationResponse> updateLocation(@Valid @RequestBody LocationUpdateRequest request) {
        LocationResponse response = locationService.updateLocation(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/current")
    public ResponseEntity<LocationResponse> getCurrentLocation() {
        LocationResponse response = locationService.getCurrentLocation();
        return ResponseEntity.ok(response);
    }

    /**
     * Get location history with optional limit
     * Example: /location/history?limit=50
     */
    @GetMapping("/history")
    public ResponseEntity<List<LocationResponse>> getLocationHistory(
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit) {
        
        if (limit > 500) {
            limit = 500; // Prevent excessive data
        }
        
        List<LocationResponse> history = locationService.getLocationHistory(limit);
        return ResponseEntity.ok(history);
    }
}