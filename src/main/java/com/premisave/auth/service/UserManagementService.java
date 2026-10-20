package com.premisave.auth.service;

import com.premisave.auth.dto.UserDto;
import com.premisave.auth.dto.UserSearchRequest;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.Role;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecuritySeverity;
import com.premisave.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import com.premisave.auth.service.security.PasswordChangeSecurityHandler;
import com.premisave.auth.service.security.PasswordResetService;
import com.premisave.auth.service.security.RequestAttributes;
import com.premisave.auth.service.security.SecurityAuditService;
import com.premisave.auth.service.security.SecurityNotificationService;
import com.premisave.auth.service.security.SessionService;
import com.premisave.auth.service.security.Tokens;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserManagementService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final SecurityAuditService audit;
    private final SecurityNotificationService notifications;
    private final PasswordChangeSecurityHandler passwordChanges;
    private final PasswordResetService passwordReset;

    public UserManagementService(UserRepository userRepository, ModelMapper modelMapper, PasswordEncoder passwordEncoder,
                                 SessionService sessionService, SecurityAuditService audit,
                                 SecurityNotificationService notifications,
                                 PasswordChangeSecurityHandler passwordChanges, PasswordResetService passwordReset) {
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.audit = audit;
        this.notifications = notifications;
        this.passwordChanges = passwordChanges;
        this.passwordReset = passwordReset;
        
        configureModelMapper();
    }
    
    private void configureModelMapper() {
        modelMapper.createTypeMap(User.class, UserDto.class)
            .addMappings(mapper -> {
                mapper.map(User::getUsername, UserDto::setUsername);
                mapper.map(User::getEmail, UserDto::setEmail);
                mapper.skip(UserDto::setPassword);
            });
            
        modelMapper.createTypeMap(UserDto.class, User.class)
            .addMappings(mapper -> {
                mapper.map(UserDto::getUsername, User::setUsername);
                mapper.map(UserDto::getEmail, User::setEmail);
                mapper.skip(User::setPassword);
            });
    }
    
    public List<UserDto> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    public UserDto createUser(UserDto userDto) {
        log.info("Creating new user with email: {}", userDto.getEmail());
        
        if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }
        
        if (userDto.getUsername() != null && 
            userRepository.findByUsername(userDto.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        
        User user = new User();
        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
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
        user.setActive(userDto.getActive() != null && userDto.getActive());
        user.setVerified(userDto.getVerified() != null && userDto.getVerified());
        user.setArchived(userDto.getArchived() != null && userDto.getArchived());
        
        boolean noPasswordGiven = userDto.getPassword() == null || userDto.getPassword().trim().isEmpty();
        if (!noPasswordGiven) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        } else {
            // No shared default password: the account gets an unknown random one and the owner
            // receives a reset link to choose their own.
            user.setPassword(passwordEncoder.encode(Tokens.newToken()));
        }
        
        user = userRepository.save(user);
        log.info("User created successfully with ID: {}", user.getId());

        if (noPasswordGiven) {
            passwordReset.sendResetFor(user);
        }
        if (user.getRole() == Role.ADMIN) {
            auditRoleChange(user, null, Role.ADMIN, "Account created with the ADMIN role through user management");
        }
        
        return convertToDto(user);
    }

    public UserDto updateUser(String id, UserDto userDto) {
        log.info("Updating user with ID: {}", id);
        
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        Role previousRole = user.getRole();
        boolean passwordChanged = false;
        
        if (userDto.getEmail() != null && !user.getEmail().equals(userDto.getEmail())) {
            if (userRepository.findByEmail(userDto.getEmail()).isPresent()) {
                throw new RuntimeException("Email already exists");
            }
            user.setEmail(userDto.getEmail());
        }
        
        if (userDto.getUsername() != null && !userDto.getUsername().isBlank()
                && !userDto.getUsername().equals(user.getUsername())) {
            if (userRepository.findByUsername(userDto.getUsername()).isPresent()) {
                throw new RuntimeException("Username already exists");
            }
            user.setUsername(userDto.getUsername());
            user.setUsernameGenerated(false);
        }
        
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
        
        if (userDto.getActive() != null) user.setActive(userDto.getActive());
        if (userDto.getVerified() != null) user.setVerified(userDto.getVerified());
        if (userDto.getArchived() != null) user.setArchived(userDto.getArchived());
        
        if (userDto.getPassword() != null && !userDto.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDto.getPassword()));
            passwordChanged = true;
        }
        
        user = userRepository.save(user);
        log.info("User updated successfully with ID: {}", user.getId());

        if (user.getRole() != previousRole) {
            auditRoleChange(user, previousRole, user.getRole(), "Updated through user management");
        }
        if (passwordChanged) {
            passwordChanges.afterPasswordChange(user, null, SecurityEventType.PASSWORD_CHANGED, actorId(),
                    "Changed by an administrator", RequestAttributes.currentClient());
        }
        
        return convertToDto(user);
    }

    public void deleteUser(String id) {
        log.info("Deleting user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        userRepository.delete(user);
        log.info("User deleted successfully");
    }

    public void archiveUser(String id) {
        log.info("Archiving user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.isArchived()) {
            throw new RuntimeException("User is already archived");
        }
        
        user.setArchived(true);
        userRepository.save(user);
        endAllSessions(user, "ACCOUNT_ARCHIVED");
        log.info("User archived successfully");
    }

    public void unarchiveUser(String id) {
        log.info("Unarchiving user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!user.isArchived()) {
            throw new RuntimeException("User is already unarchived");
        }
        
        user.setArchived(false);
        userRepository.save(user);
        log.info("User unarchived successfully");
    }

    public void activateUser(String id) {
        log.info("Activating user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.isActive()) {
            throw new RuntimeException("User is already active");
        }
        
        user.setActive(true);
        userRepository.save(user);
        log.info("User activated successfully");
    }

    public void deactivateUser(String id) {
        log.info("Deactivating user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!user.isActive()) {
            throw new RuntimeException("User is already deactivated");
        }
        
        user.setActive(false);
        userRepository.save(user);
        endAllSessions(user, "ACCOUNT_DEACTIVATED");
        log.info("User deactivated successfully");
    }

    public void verifyUser(String id) {
        log.info("Verifying user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.isVerified()) {
            throw new RuntimeException("User is already verified");
        }
        
        user.setVerified(true);
        userRepository.save(user);
        log.info("User verified successfully");
    }

    public void unverifyUser(String id) {
        log.info("Unverifying user with ID: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if (!user.isVerified()) {
            log.warn("User {} is already unverified", id);
            throw new RuntimeException("User is already unverified");
        }

        if (user.getRole() == com.premisave.auth.enums.Role.ADMIN) {
            log.warn("Attempted to unverify ADMIN user: {}", id);
            throw new RuntimeException("Cannot unverify ADMIN accounts for security reasons");
        }

        user.setVerified(false);
        userRepository.save(user);
        
        log.info("User unverified successfully: {} ({})", id, user.getEmail());
    }

    public List<UserDto> searchUsers(UserSearchRequest request) {
        log.info("Searching users with query: {}", request.getQuery());
        List<User> users = userRepository.searchUsers(request.getQuery());
        return users.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public void updatePassword(String id, String newPassword) {
        log.info("Updating password for user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        
        validatePasswordStrength(newPassword);
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        passwordChanges.afterPasswordChange(user, null, SecurityEventType.PASSWORD_CHANGED, actorId(),
                "Changed by an administrator", RequestAttributes.currentClient());
        log.info("Password updated successfully for user: {}", user.getEmail());
    }

    public void resetPassword(String id) {
        log.info("Resetting password for user with ID: {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        
        // The old password stops working at once and no replacement is ever shared: the owner gets
        // a single-use link by email and chooses a new password themselves.
        user.setPassword(passwordEncoder.encode(Tokens.newToken()));
        userRepository.save(user);

        passwordReset.sendResetFor(user);
        passwordChanges.afterPasswordChange(user, null, SecurityEventType.PASSWORD_RESET, actorId(),
                "Reset by an administrator", RequestAttributes.currentClient());
        log.info("Password reset started for user: {}", user.getEmail());
    }

    public UserDto getUserById(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDto(user);
    }

    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDto(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public void changeUserRole(String id, String role) {
        changeUserRole(id, role, null);
    }

    /** @param reason why the role is changing; recorded in the audit trail for administrator changes */
    public void changeUserRole(String id, String role, String reason) {
        log.info("Changing role to {} for user with ID: {}", role, id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            com.premisave.auth.enums.Role newRole = com.premisave.auth.enums.Role.valueOf(role.toUpperCase().trim());
            
            if (user.getRole() == newRole) {
                throw new RuntimeException("User is already a " + newRole.name() + ". No changes made.");
            }

            if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
                long adminCount = userRepository.countByRole(Role.ADMIN);
                if (adminCount <= 1) {
                    throw new RuntimeException("Cannot demote the last ADMIN user for security reasons.");
                }
            }

            Role previousRole = user.getRole();
            user.setRole(newRole);
            userRepository.save(user);
            auditRoleChange(user, previousRole, newRole,
                    reason == null || reason.isBlank() ? "No reason provided" : reason.trim());
            
            log.info("Role changed successfully from {} to {} for user: {}", 
                    previousRole, newRole, user.getEmail());
            
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid role provided: " + role + ". Allowed roles: " + 
                    java.util.Arrays.toString(Role.values()));
        }
    }

    public List<UserDto> getActiveUsers() {
        List<User> users = userRepository.findByActiveTrueAndArchivedFalse();
        return users.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> getArchivedUsers() {
        List<User> users = userRepository.findByArchivedTrue();
        return users.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private String actorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User actor && actor.getId() != null) {
            return actor.getId();
        }
        return "SYSTEM";
    }

    private void endAllSessions(User user, String reason) {
        int ended = sessionService.revokeAll(user.getId(), null, reason, actorId());
        if (ended > 0) {
            audit.event(SecurityEventType.ALL_SESSIONS_REVOKED)
                    .user(user.getId()).actor(actorId()).client(RequestAttributes.currentClient())
                    .reason(reason).detail("sessionsEnded", ended)
                    .write();
        }
    }

    /** Records administrator privilege changes (granted or removed) with actor, target, time and reason. */
    private void auditRoleChange(User target, Role previous, Role current, String reason) {
        if (previous != Role.ADMIN && current != Role.ADMIN) {
            return;
        }
        audit.event(SecurityEventType.ADMIN_ROLE_CHANGED)
                .severity(SecuritySeverity.WARNING)
                .user(target.getId())
                .actor(actorId())
                .client(RequestAttributes.currentClient())
                .reason(reason)
                .detail("previousRole", previous == null ? "NONE" : previous.name())
                .detail("newRole", current == null ? "NONE" : current.name())
                .write();
        if (current == Role.ADMIN) {
            notifications.sendAdminRoleAssigned(target.getId(), target.getEmail(), target.getFirstName(),
                    reason, java.time.Instant.now());
        } else {
            notifications.sendNotice(target.getId(), target.getEmail(), target.getFirstName(),
                    "Your Premisave administrator access was removed",
                    "Administrator access removed",
                    "Your account " + target.getEmail() + " no longer has the ADMIN role.",
                    java.util.List.of(new com.premisave.auth.service.security.EmailTemplateRenderer.Row("New role",
                            current == null ? "NONE" : current.name())),
                    "If you did not expect this change, contact support.", null, null);
        }
    }

    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setUsernameGenerated(user.isUsernameGenerated());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setMiddleName(user.getMiddleName());
        dto.setLastName(user.getLastName());
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