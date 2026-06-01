package com.premisave.auth.controller;

import com.premisave.auth.dto.ProfileViewResponse;
import com.premisave.auth.dto.ProfileViewStats;
import com.premisave.auth.dto.WhoIViewedResponse;
import com.premisave.auth.service.ProfileViewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/profile/views")
@PreAuthorize("isAuthenticated()")
public class ProfileViewController {

    private final ProfileViewService profileViewService;

    public ProfileViewController(ProfileViewService profileViewService) {
        this.profileViewService = profileViewService;
    }

    /**
     * Record profile view (called when someone visits a profile)
     */
    @PostMapping("/{targetId}")
    public ResponseEntity<ProfileViewResponse> recordView(
            @PathVariable String targetId, 
            HttpServletRequest request) {
        
        ProfileViewResponse response = profileViewService.recordProfileView(targetId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get who viewed my profile (Last 20)
     */
    @GetMapping("/who-viewed-me")
    public ResponseEntity<List<ProfileViewResponse>> getWhoViewedMe() {
        return ResponseEntity.ok(profileViewService.getWhoViewedMyProfile());
    }

    /**
     * Get profiles I have viewed (Who I Viewed)
     * Returns number of profiles configured in application.yml (profile-views.max-history-size)
     * Default is 20 if not configured
     */
    @GetMapping("/who-i-viewed")
    public ResponseEntity<List<WhoIViewedResponse>> getWhoIViewed() {
        return ResponseEntity.ok(profileViewService.getWhoIViewed());
    }

    /**
     * Get my profile view statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<ProfileViewStats> getStats() {
        return ResponseEntity.ok(profileViewService.getProfileViewStats());
    }
}