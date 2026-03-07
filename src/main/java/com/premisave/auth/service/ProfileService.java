package com.premisave.auth.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.premisave.auth.dto.PasswordChangeRequest;
import com.premisave.auth.dto.ProfileUpdateRequest;
import com.premisave.auth.dto.UserDto;
import com.premisave.auth.entity.User;
import com.premisave.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class ProfileService {

    private final UserRepository userRepository;
    private final Cloudinary cloudinary;
    private final PasswordEncoder passwordEncoder;
    
    // Allowed image content types
    private static final Set<String> ALLOWED_CONTENT_TYPES = new HashSet<>(Arrays.asList(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp"
    ));
    
    // Maximum file size in bytes (5MB)
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    
    // Cloudinary folder
    private static final String CLOUDINARY_FOLDER = "premisave/profile-photos";

    public ProfileService(UserRepository userRepository, Cloudinary cloudinary, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.cloudinary = cloudinary;
        this.passwordEncoder = passwordEncoder;
    }

    public UserDto getCurrentUserProfile() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String principalName = authentication.getName(); // This will be the email
            
            log.debug("Getting profile for authenticated user with email: {}", principalName);
            
            // Find user by email (since that's what Spring Security uses for authentication)
            User user = userRepository.findByEmail(principalName)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", principalName);
                    return new RuntimeException("User not found");
                });
            
            log.debug("Found user - Display username: '{}', Email: '{}'", 
                user.getDisplayUsername(), user.getEmail());
            
            return convertToDto(user);
            
        } catch (Exception e) {
            log.error("Error getting current user profile: ", e);
            throw new RuntimeException("Failed to get user profile: " + e.getMessage());
        }
    }

    public void updateProfile(ProfileUpdateRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principalName = authentication.getName(); // Email
        
        User user = userRepository.findByEmail(principalName)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Update display username if provided and different
        if (request.getUsername() != null && !request.getUsername().equals(user.getDisplayUsername())) {
            // Check if new display username is available
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new RuntimeException("Username already taken");
            }
            user.setDisplayUsername(request.getUsername());
        }
        
        // Update other fields
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getMiddleName() != null) user.setMiddleName(request.getMiddleName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
        if (request.getAddress1() != null) user.setAddress1(request.getAddress1());
        if (request.getAddress2() != null) user.setAddress2(request.getAddress2());
        if (request.getCountry() != null) user.setCountry(request.getCountry());
        if (request.getLanguage() != null) {
            try {
                user.setLanguage(com.premisave.auth.enums.Language.valueOf(request.getLanguage().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid language value: " + request.getLanguage());
            }
        }
        
        userRepository.save(user);
        log.info("Profile updated for user: {}", user.getEmail());
    }

    @SuppressWarnings("rawtypes")
    public String uploadProfilePic(MultipartFile file) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principalName = authentication.getName();
        
        User user = userRepository.findByEmail(principalName)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        try {
            // Validate file is not empty
            if (file.isEmpty()) {
                throw new RuntimeException("Please select a file to upload");
            }
            
            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
                throw new RuntimeException("Only image files are allowed (JPEG, PNG, GIF, WEBP)");
            }
            
            // Validate file size
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new RuntimeException("File size must be less than 5MB");
            }
            
            // Get file extension for validation
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null) {
                String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
                if (!Arrays.asList("jpg", "jpeg", "png", "gif", "webp").contains(extension)) {
                    throw new RuntimeException("Invalid file extension. Allowed: jpg, jpeg, png, gif, webp");
                }
            }
            
            // Delete old profile picture from Cloudinary if exists
            deleteOldProfilePicture(user.getProfilePictureUrl());
            
            // Generate unique public ID for the new image
            String publicId = CLOUDINARY_FOLDER + "/user_" + user.getId() + "_" + System.currentTimeMillis();
            
            // Upload new profile picture to Cloudinary
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), 
                ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", CLOUDINARY_FOLDER,
                    "public_id", publicId,
                    "transformation", new com.cloudinary.Transformation()
                        .width(200).height(200).crop("fill").gravity("face").quality("auto")
                ));
            
            String url = (String) uploadResult.get("secure_url");
            user.setProfilePictureUrl(url);
            userRepository.save(user);
            
            log.info("Profile picture uploaded for user: {} to folder: {}", user.getEmail(), CLOUDINARY_FOLDER);
            return url;
            
        } catch (IOException e) {
            log.error("Failed to upload profile picture for user: {}", user.getEmail(), e);
            throw new RuntimeException("Upload failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error uploading profile picture for user: {}", user.getEmail(), e);
            throw new RuntimeException("Upload failed: " + e.getMessage());
        }
    }
    
    /**
     * Delete old profile picture from Cloudinary
     */
    private void deleteOldProfilePicture(String oldProfilePictureUrl) {
        try {
            if (oldProfilePictureUrl != null && !oldProfilePictureUrl.isEmpty()) {
                // Extract public ID from URL
                // Cloudinary URL format: https://res.cloudinary.com/cloudname/image/upload/v1234567890/folder/public_id.jpg
                String[] parts = oldProfilePictureUrl.split("/");
                if (parts.length > 0) {
                    String publicIdWithExtension = parts[parts.length - 1];
                    String publicId = publicIdWithExtension.substring(0, publicIdWithExtension.lastIndexOf("."));
                    
                    // If the image is in a folder, reconstruct the full public ID
                    // Check if this is a Cloudinary URL by looking for 'upload' segment
                    int uploadIndex = -1;
                    for (int i = 0; i < parts.length; i++) {
                        if ("upload".equals(parts[i])) {
                            uploadIndex = i;
                            break;
                        }
                    }
                    
                    if (uploadIndex != -1 && uploadIndex + 2 < parts.length) {
                        // Get the folder path after 'upload'
                        StringBuilder folderPath = new StringBuilder();
                        for (int i = uploadIndex + 1; i < parts.length - 1; i++) {
                            if (folderPath.length() > 0) {
                                folderPath.append("/");
                            }
                            folderPath.append(parts[i]);
                        }
                        publicId = folderPath.toString();
                    }
                    
                    // Delete from Cloudinary
                    Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                    log.info("Deleted old profile picture: {} - Result: {}", publicId, result.get("result"));
                }
            }
        } catch (Exception e) {
            // Log but don't throw - we don't want to fail the new upload if old delete fails
            log.warn("Failed to delete old profile picture from URL {}: {}", oldProfilePictureUrl, e.getMessage());
        }
    }

    public void updatePassword(String currentPassword, String newPassword, String confirmPassword) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principalName = authentication.getName(); // Email
        
        User user = userRepository.findByEmail(principalName)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Validate current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("Incorrect current password attempt for user: {}", user.getEmail());
            throw new RuntimeException("Current password is incorrect");
        }
        
        // Validate new password and confirmation match
        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("New password and confirmation do not match");
        }
        
        // Validate new password is different from current password
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new RuntimeException("New password must be different from current password");
        }
        
        // Validate password strength
        validatePasswordStrength(newPassword);
        
        // Encode and set new password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        log.info("Password updated successfully for user: {}", user.getEmail());
    }

    private void validatePasswordStrength(String password) {
        if (password.length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters long");
        }
        
        if (!password.matches(".*[A-Z].*")) {
            throw new RuntimeException("Password must contain at least one uppercase letter");
        }
        
        if (!password.matches(".*[a-z].*")) {
            throw new RuntimeException("Password must contain at least one lowercase letter");
        }
        
        if (!password.matches(".*\\d.*")) {
            throw new RuntimeException("Password must contain at least one digit");
        }
        
        if (!password.matches(".*[@#$%^&+=!].*")) {
            throw new RuntimeException("Password must contain at least one special character (@#$%^&+=!)");
        }
    }

    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId().toString());
        
        // Get the display username (not the Spring Security username which is email)
        dto.setUsername(user.getDisplayUsername()); // This will return "graham_admin"
        
        dto.setFirstName(user.getFirstName());
        dto.setMiddleName(user.getMiddleName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setAddress1(user.getAddress1());
        dto.setAddress2(user.getAddress2());
        dto.setCountry(user.getCountry());
        dto.setLanguage(user.getLanguage());
        dto.setProfilePictureUrl(user.getProfilePictureUrl());
        dto.setRole(user.getRole());
        dto.setActive(user.isActive());
        dto.setVerified(user.isVerified());
        dto.setArchived(user.isArchived());
        // Don't set password for security
        
        log.debug("Converted to DTO - Display Username: '{}', Email: '{}'", 
            dto.getUsername(), dto.getEmail());
        
        return dto;
    }
}