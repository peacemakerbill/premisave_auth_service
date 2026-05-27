package com.premisave.auth.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.premisave.auth.dto.ProfileUpdateRequest;
import com.premisave.auth.dto.ProfileUploadResponse;
import com.premisave.auth.dto.UserDto;
import com.premisave.auth.entity.User;
import com.premisave.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class ProfileService {

	private final UserRepository userRepository;
	private final Cloudinary cloudinary;
	private final PasswordEncoder passwordEncoder;

	// Allowed image content types
	private static final Set<String> ALLOWED_CONTENT_TYPES = new HashSet<>(
			Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp"));

	@Value("${spring.servlet.multipart.max-file-size:10MB}")
	private String maxFileSizeStr;

	// Parsed max file size in bytes
	private long maxFileSizeBytes;

	public ProfileService(UserRepository userRepository, Cloudinary cloudinary, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.cloudinary = cloudinary;
		this.passwordEncoder = passwordEncoder;
		this.maxFileSizeBytes = parseMaxFileSize();
	}

	/**
	 * Parse Spring's max-file-size property (supports 10MB, 5KB, etc.)
	 */
	private long parseMaxFileSize() {
		if (maxFileSizeStr == null || maxFileSizeStr.isEmpty()) {
			return 10 * 1024 * 1024; // 10MB default
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
				// Assume bytes if no unit
				return Long.parseLong(value);
			}
		} catch (Exception e) {
			System.err.println("Failed to parse max-file-size: " + maxFileSizeStr + ". Using 10MB default.");
			return 10 * 1024 * 1024;
		}
	}

	/**
	 * Upload profile picture - Synchronous
	 */
	public ProfileUploadResponse uploadProfilePic(MultipartFile file) {
		try {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			String email = authentication.getName();

			User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

			// Validation
			if (file.isEmpty()) {
				return new ProfileUploadResponse("Please select a file to upload", null, false);
			}
			if (file.getSize() > maxFileSizeBytes) {
				return new ProfileUploadResponse("File size must be less than " + maxFileSizeStr, null, false);
			}
			if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
				return new ProfileUploadResponse("Only image files (JPEG, PNG, GIF, WEBP) are allowed", null, false);
			}

			// Delete old profile picture if exists
			if (user.getProfilePictureUrl() != null && !user.getProfilePictureUrl().isEmpty()) {
				deleteOldProfilePicture(user.getProfilePictureUrl());
			}

			// Generate unique public ID - WITHOUT duplicating folder
			String publicId = "user_" + user.getId() + "_" + System.currentTimeMillis();

			// Upload new image with optimization
			@SuppressWarnings("rawtypes")
			Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap("public_id", publicId,
					"folder", "premisave/profile-photos", "transformation", "w_400,h_400,c_fill,q_auto,f_auto"));

			String newUrl = (String) uploadResult.get("secure_url");

			// Update user
			user.setProfilePictureUrl(newUrl);
			userRepository.save(user);

			return new ProfileUploadResponse("Profile picture uploaded successfully", newUrl, true);

		} catch (Exception e) {
			e.printStackTrace();
			return new ProfileUploadResponse("Failed to upload profile picture: " + e.getMessage(), null, false);
		}
	}

	/**
	 * Deletes old profile picture from Cloudinary
	 */
	private void deleteOldProfilePicture(String oldUrl) {
		if (oldUrl == null || oldUrl.isEmpty()) {
			return;
		}

		try {
			String publicId = extractPublicIdFromUrl(oldUrl);
			if (publicId != null) {
				@SuppressWarnings("rawtypes")
				Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
				System.out.println("Old profile picture deleted successfully: " + publicId);
			}
		} catch (Exception e) {
			System.err.println("Failed to delete old profile picture: " + e.getMessage());
		}
	}

	/**
	 * Extracts Cloudinary public_id from URL
	 */
	private String extractPublicIdFromUrl(String url) {
		try {
			String[] parts = url.split("/");
			boolean foundUpload = false;
			StringBuilder publicId = new StringBuilder();

			for (String part : parts) {
				if (foundUpload) {
					if (publicId.length() > 0) {
						publicId.append("/");
					}
					publicId.append(part);
				}
				if ("upload".equals(part)) {
					foundUpload = true;
				}
			}

			String id = publicId.toString();
			if (id.contains(".")) {
				id = id.substring(0, id.lastIndexOf("."));
			}

			return id.isEmpty() ? null : id;
		} catch (Exception e) {
			return null;
		}
	}

	public UserDto getCurrentUserProfile() {
		try {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			String email = authentication.getName();

			User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

			return convertToDto(user);
		} catch (Exception e) {
			throw new RuntimeException("Failed to get user profile: " + e.getMessage());
		}
	}

	public void updateProfile(ProfileUpdateRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String email = authentication.getName();

		User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

		if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
			if (userRepository.existsByUsername(request.getUsername())) {
				throw new RuntimeException("Username already taken");
			}
			user.setUsername(request.getUsername());
		}

		if (request.getFirstName() != null)
			user.setFirstName(request.getFirstName());
		if (request.getMiddleName() != null)
			user.setMiddleName(request.getMiddleName());
		if (request.getLastName() != null)
			user.setLastName(request.getLastName());
		if (request.getPhoneNumber() != null)
			user.setPhoneNumber(request.getPhoneNumber());
		if (request.getAddress1() != null)
			user.setAddress1(request.getAddress1());
		if (request.getAddress2() != null)
			user.setAddress2(request.getAddress2());
		if (request.getCountry() != null)
			user.setCountry(request.getCountry());

		if (request.getLanguage() != null) {
			try {
				user.setLanguage(com.premisave.auth.enums.Language.valueOf(request.getLanguage().toUpperCase()));
			} catch (IllegalArgumentException e) {
				throw new RuntimeException("Invalid language value");
			}
		}

		userRepository.save(user);
	}

	public void updatePassword(String currentPassword, String newPassword, String confirmPassword) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String email = authentication.getName();

		User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

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
		dto.setUsername(user.getUsername());
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