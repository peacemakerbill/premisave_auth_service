package com.premisave.auth.service;

import com.premisave.auth.dto.UserDto;
import com.premisave.auth.dto.UserSearchRequest;
import com.premisave.auth.entity.User;
import com.premisave.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserManagementService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(UserRepository userRepository, ModelMapper modelMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
        this.passwordEncoder = passwordEncoder;
        
        // Configure ModelMapper to handle our custom mappings
        configureModelMapper();
    }
    
    private void configureModelMapper() {
        // Custom mapping for User -> UserDto
        modelMapper.createTypeMap(User.class, UserDto.class)
            .addMappings(mapper -> {
                // Map displayUsername to username in DTO
                mapper.map(User::getDisplayUsername, UserDto::setUsername);
                // Map email field to email in DTO
                mapper.map(User::getEmail, UserDto::setEmail);
                // Don't map password for security
                mapper.skip(UserDto::setPassword);
            });
            
        // Custom mapping for UserDto -> User
        modelMapper.createTypeMap(UserDto.class, User.class)
            .addMappings(mapper -> {
                // Map username from DTO to displayUsername in User
                mapper.map(UserDto::getUsername, User::setDisplayUsername);
                // Map email from DTO to email in User
                mapper.map(UserDto::getEmail, User::setEmail);
                // Handle password separately
                mapper.skip(User::setPassword);
            });
    }
    
    public List<UserDto> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public UserDto createUser(UserDto userDto) {
        log.info("Creating new user with email: {}", userDto.getEmail());
        
        // Check if email already exists
        if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }
        
        // Check if display username already exists
        if (userDto.getUsername() != null && 
            userRepository.findByUsername(userDto.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        
        // Create new user
        User user = new User();
        
        // Set display username
        if (userDto.getUsername() != null) {
            user.setDisplayUsername(userDto.getUsername());
        }
        
        // Set email
        user.setEmail(userDto.getEmail());
        
        // Set other fields
        user.setFirstName(userDto.getFirstName());
        user.setMiddleName(userDto.getMiddleName());
        user.setLastName(userDto.getLastName());
        user.setPhoneNumber(userDto.getPhoneNumber());
        user.setAddress1(userDto.getAddress1());
        user.setAddress2(userDto.getAddress2());
        user.setCountry(userDto.getCountry());
        user.setLanguage(userDto.getLanguage());
        user.setProfilePictureUrl(userDto.getProfilePictureUrl());
        user.setRole(userDto.getRole());
        user.setActive(userDto.isActive());
        user.setVerified(userDto.isVerified());
        user.setArchived(userDto.isArchived());
        
        // Handle password
        if (userDto.getPassword() != null && !userDto.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        } else {
            // Set default password if not provided
            user.setPassword(passwordEncoder.encode("TempPassword123!"));
        }
        
        user = userRepository.save(user);
        log.info("User created successfully with ID: {}", user.getId());
        
        return convertToDto(user);
    }

    @Transactional
    public UserDto updateUser(String id, UserDto userDto) {
        log.info("Updating user with ID: {}", id);
        
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        // Check if email is being changed and if it already exists
        if (userDto.getEmail() != null && !user.getEmail().equals(userDto.getEmail())) {
            if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
                throw new RuntimeException("Email already exists");
            }
            user.setEmail(userDto.getEmail());
        }
        
        // Check if display username is being changed and if it already exists
        if (userDto.getUsername() != null && !user.getDisplayUsername().equals(userDto.getUsername())) {
            if (userRepository.findByUsername(userDto.getUsername()).isPresent()) {
                throw new RuntimeException("Username already exists");
            }
            user.setDisplayUsername(userDto.getUsername());
        }
        
        // Update other fields if provided
        if (userDto.getFirstName() != null) user.setFirstName(userDto.getFirstName());
        if (userDto.getMiddleName() != null) user.setMiddleName(userDto.getMiddleName());
        if (userDto.getLastName() != null) user.setLastName(userDto.getLastName());
        if (userDto.getPhoneNumber() != null) user.setPhoneNumber(userDto.getPhoneNumber());
        if (userDto.getAddress1() != null) user.setAddress1(userDto.getAddress1());
        if (userDto.getAddress2() != null) user.setAddress2(userDto.getAddress2());
        if (userDto.getCountry() != null) user.setCountry(userDto.getCountry());
        if (userDto.getLanguage() != null) user.setLanguage(userDto.getLanguage());
        if (userDto.getProfilePictureUrl() != null) user.setProfilePictureUrl(userDto.getProfilePictureUrl());
        if (userDto.getRole() != null) user.setRole(userDto.getRole());
        
        // Update status fields
        user.setActive(userDto.isActive());
        user.setVerified(userDto.isVerified());
        user.setArchived(userDto.isArchived());
        
        // Handle password update separately
        if (userDto.getPassword() != null && !userDto.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }
        
        user = userRepository.save(user);
        log.info("User updated successfully with ID: {}", user.getId());
        
        return convertToDto(user);
    }

    @Transactional
    public void deleteUser(String id) {
        log.info("Deleting user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        userRepository.delete(user);
        log.info("User deleted successfully");
    }

    @Transactional
    public void archiveUser(String id) {
        log.info("Archiving user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setArchived(true);
        userRepository.save(user);
        log.info("User archived successfully");
    }

    @Transactional
    public void unarchiveUser(String id) {
        log.info("Unarchiving user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setArchived(false);
        userRepository.save(user);
        log.info("User unarchived successfully");
    }

    @Transactional
    public void activateUser(String id) {
        log.info("Activating user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        userRepository.save(user);
        log.info("User activated successfully");
    }

    @Transactional
    public void deactivateUser(String id) {
        log.info("Deactivating user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        userRepository.save(user);
        log.info("User deactivated successfully");
    }

    @Transactional
    public void verifyUser(String id) {
        log.info("Verifying user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setVerified(true);
        userRepository.save(user);
        log.info("User verified successfully");
    }

    @Transactional
    public void unverifyUser(String id) {
        log.info("Unverifying user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setVerified(false);
        userRepository.save(user);
        log.info("User unverified successfully");
    }

    public List<UserDto> searchUsers(UserSearchRequest request) {
        log.info("Searching users with query: {}", request.getQuery());
        List<User> users = userRepository.searchUsers(request.getQuery());
        return users.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updatePassword(String id, String newPassword) {
        log.info("Updating password for user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        
        // Validate password strength
        validatePasswordStrength(newPassword);
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password updated successfully for user: {}", user.getEmail());
    }

    @Transactional
    public void resetPassword(String id) {
        log.info("Resetting password for user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        // Generate a temporary password
        String temporaryPassword = "TempPassword123!";
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        
        userRepository.save(user);
        log.info("Password reset successfully for user: {}", user.getEmail());
        // TODO: Send email notification with temporary password
    }

    public UserDto getUserById(String id) {
        log.debug("Getting user by ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDto(user);
    }

    public UserDto getUserByEmail(String email) {
        log.debug("Getting user by email: {}", email);
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDto(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    @Transactional
    public void changeUserRole(String id, String role) {
        log.info("Changing role to {} for user with ID: {}", role, id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        try {
            com.premisave.auth.enums.Role newRole = com.premisave.auth.enums.Role.valueOf(role.toUpperCase());
            user.setRole(newRole);
            userRepository.save(user);
            log.info("Role changed successfully to {} for user: {}", role, user.getEmail());
        } catch (IllegalArgumentException e) {
            log.error("Invalid role provided: {}", role);
            throw new RuntimeException("Invalid role: " + role);
        }
    }

    public List<UserDto> getActiveUsers() {
        log.debug("Getting all active users");
        List<User> users = userRepository.findByActiveTrueAndArchivedFalse();
        return users.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getArchivedUsers() {
        log.debug("Getting all archived users");
        List<User> users = userRepository.findByArchivedTrue();
        return users.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Custom conversion method to handle the display username mapping
     */
    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        
        // Map basic fields
        dto.setId(user.getId());
        dto.setUsername(user.getDisplayUsername()); // Use display username
        dto.setEmail(user.getEmail()); // Use email field
        
        // Map personal information
        dto.setFirstName(user.getFirstName());
        dto.setMiddleName(user.getMiddleName());
        dto.setLastName(user.getLastName());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setAddress1(user.getAddress1());
        dto.setAddress2(user.getAddress2());
        dto.setCountry(user.getCountry());
        dto.setLanguage(user.getLanguage());
        dto.setProfilePictureUrl(user.getProfilePictureUrl());
        
        // Map role and status
        dto.setRole(user.getRole());
        dto.setActive(user.isActive());
        dto.setVerified(user.isVerified());
        dto.setArchived(user.isArchived());
        
        // Don't map password for security
        dto.setPassword(null);
        
        log.debug("Converted User to DTO - Username: '{}', Email: '{}'", 
            dto.getUsername(), dto.getEmail());
        
        return dto;
    }

    /**
     * Password strength validation
     */
    private void validatePasswordStrength(String password) {
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must contain at least one digit");
        }
        
        if (!password.matches(".*[@#$%^&+=!].*")) {
            throw new IllegalArgumentException("Password must contain at least one special character (@#$%^&+=!)");
        }
    }
}