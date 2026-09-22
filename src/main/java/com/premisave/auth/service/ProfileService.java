package com.premisave.auth.service;

import com.premisave.auth.dto.ProfileUpdateRequest;
import com.premisave.auth.dto.ProfileUploadResponse;
import com.premisave.auth.dto.UserDirectoryDto;
import com.premisave.auth.dto.UserDto;
import com.premisave.auth.entity.User;
import com.premisave.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final ProfilePictureStorage pictureStorage;
    private final PasswordEncoder passwordEncoder;

    private static final Set<String> ALLOWED_CONTENT_TYPES = new HashSet<>(
            Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp"));

    private final String maxFileSizeStr;
    private final long maxFileSizeBytes;

    /*
     * max-file-size is injected through the constructor: with field
     * injection it is still null while the constructor runs, so the
     * configured limit was never actually parsed.
     */
    public ProfileService(UserRepository userRepository,
                          ProfilePictureStorage pictureStorage,
                          PasswordEncoder passwordEncoder,
                          @Value("${spring.servlet.multipart.max-file-size:10MB}") String maxFileSizeStr) {
        this.userRepository = userRepository;
        this.pictureStorage = pictureStorage;
        this.passwordEncoder = passwordEncoder;
        this.maxFileSizeStr = maxFileSizeStr;
        this.maxFileSizeBytes = parseMaxFileSize();
    }

    private long parseMaxFileSize() {
        if (maxFileSizeStr == null || maxFileSizeStr.isEmpty()) {
            return 10 * 1024 * 1024;
        }

        String value = maxFileSizeStr.toUpperCase().trim();
        try {
            if (value.endsWith("MB")) {
                double mb = Double.parseDouble(value.replace("MB", "").trim());
                return (long) (mb * 1024 * 1024);
            } else if (value.endsWith("KB")) {
                double kb = Double.parseDouble(value.replace("KB", "").trim());
                return (long) (kb * 1024);
            } else if (value.endsWith("B")) {
                return Long.parseLong(value.replace("B", "").trim());
            } else {
                return Long.parseLong(value);
            }
        } catch (Exception e) {
            log.warn("Failed to parse max-file-size: {}. Using 10MB default.", maxFileSizeStr);
            return 10 * 1024 * 1024;
        }
    }

    public void updateProfile(ProfileUpdateRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            String newUsername = request.getUsername().trim();
            String currentUsername = user.getDisplayUsername();

            if (currentUsername == null || !newUsername.equalsIgnoreCase(currentUsername)) {
                if (userRepository.existsByUsername(newUsername)) {
                    throw new RuntimeException("Username already taken by another user");
                }
                user.setUsername(newUsername);
            }
        }

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName().trim());
        if (request.getMiddleName() != null) user.setMiddleName(request.getMiddleName().trim());
        if (request.getLastName() != null) user.setLastName(request.getLastName().trim());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber().trim());
        if (request.getAddress1() != null) user.setAddress1(request.getAddress1().trim());
        if (request.getAddress2() != null) user.setAddress2(request.getAddress2().trim());
        if (request.getCountry() != null) user.setCountry(request.getCountry().trim());

        if (request.getLanguage() != null) {
            try {
                user.setLanguage(com.premisave.auth.enums.Language.valueOf(
                        request.getLanguage().toUpperCase().trim()));
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid language value");
            }
        }

        userRepository.save(user);
    }

    /**
     * Validates the file eagerly (fast, in-thread), saves the final CDN URL
     * immediately, and hands the actual Cloudinary upload to
     * ProfilePictureStorage.uploadAsync, which runs on a background thread.
     * The caller gets an immediate response with the new URL.
     */
    public ProfileUploadResponse uploadProfilePic(MultipartFile file) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Fast validation — no network I/O
        if (file.isEmpty()) {
            return new ProfileUploadResponse("Please select a file to upload", null, false);
        }
        if (file.getSize() > maxFileSizeBytes) {
            return new ProfileUploadResponse("File size must be less than " + maxFileSizeStr, null, false);
        }
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            return new ProfileUploadResponse("Only image files (JPEG, PNG, GIF, WEBP) are allowed", null, false);
        }

        // Read bytes now — MultipartFile temp storage is gone after the request thread ends
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            return new ProfileUploadResponse("Failed to read file: " + e.getMessage(), null, false);
        }

        // The delivery URL is deterministic, so it can be saved before the upload finishes
        String publicId = pictureStorage.newPublicId(user.getId());
        String newUrl = pictureStorage.deliveryUrl(publicId);

        String previousUrl = user.getProfilePictureUrl();
        user.setProfilePictureUrl(newUrl);
        userRepository.save(user);

        // Separate bean, so @Async applies. Deletes the previous picture on success,
        // reverts the user's URL on failure.
        pictureStorage.uploadAsync(fileBytes, publicId, user.getId(), previousUrl);

        return new ProfileUploadResponse("Profile picture uploaded successfully", newUrl, true);
    }

    public UserDto getCurrentUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return convertToDto(user);
    }

    public UserDto getUserPublicProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserDto dto = convertToDto(user);
        dto.setPassword(null);

        return dto;
    }

    public List<UserDto> searchUsers(String query) {
        List<User> users = userRepository.searchUsers(query);
        return users.stream()
                .filter(user -> user.isActive() && !user.isArchived())
                .map(this::convertToPublicDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getAllUsers() {
        List<User> users = userRepository.findByActiveTrueAndArchivedFalse();
        return users.stream()
                .map(this::convertToPublicDto)
                .collect(Collectors.toList());
    }

    public UserDto convertToPublicDto(User user) {
        UserDto dto = convertToDto(user);
        dto.setEmail(null);
        dto.setPhoneNumber(null);
        dto.setAddress1(null);
        dto.setAddress2(null);
        dto.setPassword(null);
        return dto;
    }

    public List<UserDirectoryDto> getPublicUserDirectory() {
        return userRepository.findByActiveTrueAndArchivedFalse().stream()
                .map(user -> new UserDirectoryDto(
                        user.getId(),
                        (user.getFirstName() + " " + (user.getMiddleName() != null ? user.getMiddleName() + " " : "") + user.getLastName()).trim(),
                        user.getEmail(),
                        user.getPhoneNumber(),
                        user.getProfilePictureUrl()))
                .collect(Collectors.toList());
    }

    public void updatePassword(String currentPassword, String newPassword, String confirmPassword) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("New password and confirmation do not match");
        }

        if (newPassword.length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters long");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getDisplayUsername());
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
        dto.setPassword(null);

        return dto;
    }
}