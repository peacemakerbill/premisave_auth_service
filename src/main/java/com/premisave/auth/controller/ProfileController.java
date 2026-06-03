package com.premisave.auth.controller;

import com.premisave.auth.dto.PasswordChangeRequest;
import com.premisave.auth.dto.ProfileUpdateRequest;
import com.premisave.auth.dto.ProfileUploadResponse;
import com.premisave.auth.dto.UserDto;
import com.premisave.auth.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> getCurrentUserProfile() {
        UserDto userDto = profileService.getCurrentUserProfile();
        return ResponseEntity.ok(userDto);
    }

    /**
     * public profile of another user
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> getUserPublicProfile(@PathVariable String userId) {
        UserDto userDto = profileService.getUserPublicProfile(userId);
        return ResponseEntity.ok(userDto);
    }

    /**
     * NEW: Search users by name, username, email, etc.
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserDto>> searchUsers(@RequestParam String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        List<UserDto> users = profileService.searchUsers(query.trim());
        return ResponseEntity.ok(users);
    }

    /**
     * List all active users (for browsing/discovery)
     */
    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        List<UserDto> users = profileService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/update")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        profileService.updateProfile(request);
        return ResponseEntity.ok("Profile updated successfully");
    }

    @PostMapping("/upload-profile-picture")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileUploadResponse> uploadProfilePicture(@RequestParam("file") MultipartFile file) {
        ProfileUploadResponse response = profileService.uploadProfilePic(file);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        profileService.updatePassword(
            request.getCurrentPassword(), 
            request.getNewPassword(), 
            request.getConfirmPassword()
        );
        return ResponseEntity.ok("Password changed successfully");
    }
}