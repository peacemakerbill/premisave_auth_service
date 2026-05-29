package com.premisave.auth.service;

import com.premisave.auth.dto.LocationResponse;
import com.premisave.auth.dto.LocationUpdateRequest;
import com.premisave.auth.entity.User;
import com.premisave.auth.entity.UserLocation;
import com.premisave.auth.repository.UserLocationRepository;
import com.premisave.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LocationService {

    private final UserLocationRepository locationRepository;
    private final UserRepository userRepository;

    public LocationService(UserLocationRepository locationRepository, UserRepository userRepository) {
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Update or set current location
     */
    public LocationResponse updateLocation(LocationUpdateRequest request) {
        User user = getCurrentUser();

        // Deactivate previous current location
        locationRepository.deleteByUserIdAndIsCurrentTrue(user.getId());

        UserLocation location = new UserLocation();
        location.setUser(user);
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setLocationName(request.getLocationName());
        location.setAddress(request.getAddress());
        location.setCountry(request.getCountry());
        location.setCity(request.getCity());
        location.setCurrent(true);

        UserLocation saved = locationRepository.save(location);

        log.info("Location updated for user: {} | Lat: {}, Lng: {}", 
                user.getId(), request.getLatitude(), request.getLongitude());

        return convertToResponse(saved);
    }

    /**
     * Get current active location of logged-in user
     */
    public LocationResponse getCurrentLocation() {
        User user = getCurrentUser();
        UserLocation location = locationRepository.findByUserIdAndIsCurrentTrue(user.getId())
                .orElseThrow(() -> new RuntimeException("No location found. Please update your location."));

        return convertToResponse(location);
    }

    /**
     * Get location history for current user
     */
    public List<LocationResponse> getLocationHistory() {
        User user = getCurrentUser();
        List<UserLocation> history = locationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return history.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    private LocationResponse convertToResponse(UserLocation location) {
        LocationResponse response = new LocationResponse();
        response.setId(location.getId());
        response.setLatitude(location.getLatitude());
        response.setLongitude(location.getLongitude());
        response.setLocationName(location.getLocationName());
        response.setAddress(location.getAddress());
        response.setCountry(location.getCountry());
        response.setCity(location.getCity());
        response.setCurrent(location.isCurrent());   // Fixed: Use isCurrent()
        response.setCreatedAt(location.getCreatedAt());
        response.setUpdatedAt(location.getUpdatedAt());
        return response;
    }
}