package com.premisave.auth.controller;

import com.premisave.auth.dto.PasswordChangeRequest;
import com.premisave.auth.dto.ProfileUpdateRequest;
import com.premisave.auth.dto.UserDto;
import com.premisave.auth.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PutMapping("/update")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        profileService.updateProfile(request);
        return ResponseEntity.ok("Profile updated successfully");
    }

    @PostMapping("/upload-profile-picture")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> uploadProfilePicture(@RequestParam("file") MultipartFile file) {
        String url = profileService.uploadProfilePic(file);
        return ResponseEntity.ok(url);
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