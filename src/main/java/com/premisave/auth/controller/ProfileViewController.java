package com.premisave.auth.controller;

import com.premisave.auth.dto.ProfileViewResponse;
import com.premisave.auth.dto.ProfileViewStats;
import com.premisave.auth.dto.PublicProfileViewStats;
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
     */
    @GetMapping("/who-i-viewed")
    public ResponseEntity<List<WhoIViewedResponse>> getWhoIViewed() {
        return ResponseEntity.ok(profileViewService.getWhoIViewed());
    }

    /**
     * Get my own profile view statistics
     */
    @GetMapping("/my-stats")
    public ResponseEntity<ProfileViewStats> getMyStats() {
        return ResponseEntity.ok(profileViewService.getMyProfileViewStats());
    }

    /**
     * Get stats — returns current user's full stats if no userId provided,
     * or another user's public stats (totalViews only) if userId is supplied.
     * GET /profile/views/stats
     * GET /profile/views/stats?userId=abc123
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@RequestParam(required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.ok(profileViewService.getMyProfileViewStats());
        }
        return ResponseEntity.ok(profileViewService.getOtherUserProfileViewStats(userId));
    }

    /**
     * Get another user's public profile view statistics (totalViews only)
     */
    @GetMapping("/stats/{userId}")
    public ResponseEntity<PublicProfileViewStats> getUserStats(@PathVariable String userId) {
        return ResponseEntity.ok(profileViewService.getOtherUserProfileViewStats(userId));
    }
}