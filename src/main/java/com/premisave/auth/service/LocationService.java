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
import java.util.Optional;
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
     * Update current location while preserving full history
     */
    public LocationResponse updateLocation(LocationUpdateRequest request) {
        User user = getCurrentUser();

        // Mark previous current location as historical
        Optional<UserLocation> previousCurrentOpt = locationRepository.findByUserIdAndIsCurrentTrue(user.getId());
        previousCurrentOpt.ifPresent(previous -> {
            previous.setCurrent(false);
            locationRepository.save(previous);
            log.info("Previous location marked as historical for user: {}", user.getId());
        });

        // Create new current location
        UserLocation newLocation = new UserLocation();
        newLocation.setUser(user);
        newLocation.setLatitude(request.getLatitude());
        newLocation.setLongitude(request.getLongitude());
        newLocation.setLocationName(request.getLocationName());
        newLocation.setAddress(request.getAddress());
        newLocation.setCountry(request.getCountry());
        newLocation.setCity(request.getCity());
        newLocation.setCurrent(true);

        UserLocation saved = locationRepository.save(newLocation);

        log.info("New current location saved for user: {} | Lat: {}, Lng: {}", 
                user.getId(), request.getLatitude(), request.getLongitude());

        return convertToResponse(saved);
    }

    /**
     * Get current active location
     */
    public LocationResponse getCurrentLocation() {
        User user = getCurrentUser();
        
        UserLocation location = locationRepository.findByUserIdAndIsCurrentTrue(user.getId())
                .orElseThrow(() -> new RuntimeException("No current location found. Please update your location first."));

        return convertToResponse(location);
    }

    /**
     * Get full location history (newest first)
     */
    public List<LocationResponse> getLocationHistory() {
        User user = getCurrentUser();
        List<UserLocation> history = locationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return history.stream()
                      .map(this::convertToResponse)
                      .collect(Collectors.toList());
    }

    /**
     * Get limited location history
     */
    public List<LocationResponse> getLocationHistory(int limit) {
        User user = getCurrentUser();
        List<UserLocation> history = locationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return history.stream()
                      .limit(limit)
                      .map(this::convertToResponse)
                      .collect(Collectors.toList());
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
        response.setCurrent(location.isCurrent());
        response.setCreatedAt(location.getCreatedAt());
        response.setUpdatedAt(location.getUpdatedAt());
        return response;
    }
}