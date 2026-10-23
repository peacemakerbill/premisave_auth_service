package com.premisave.auth.controller;

import com.premisave.auth.dto.UserDto;
import com.premisave.auth.dto.UserSearchRequest;
import com.premisave.auth.service.UserManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userManagementService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userManagementService.getUserById(id));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UserDto> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userManagementService.getUserByEmail(email));
    }

    @PostMapping("/create")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserDto userDto) {
        return ResponseEntity.ok(userManagementService.createUser(userDto));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable String id, @Valid @RequestBody UserDto userDto) {
        return ResponseEntity.ok(userManagementService.updateUser(id, userDto));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable String id) {
        userManagementService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    @PutMapping("/archive/{id}")
    public ResponseEntity<Map<String, String>> archiveUser(@PathVariable String id) {
        userManagementService.archiveUser(id);
        return ResponseEntity.ok(Map.of("message", "User archived successfully"));
    }

    @PutMapping("/unarchive/{id}")
    public ResponseEntity<Map<String, String>> unarchiveUser(@PathVariable String id) {
        userManagementService.unarchiveUser(id);
        return ResponseEntity.ok(Map.of("message", "User unarchived successfully"));
    }

    @PutMapping("/activate/{id}")
    public ResponseEntity<Map<String, String>> activateUser(@PathVariable String id) {
        userManagementService.activateUser(id);
        return ResponseEntity.ok(Map.of("message", "User activated successfully"));
    }

    @PutMapping("/deactivate/{id}")
    public ResponseEntity<Map<String, String>> deactivateUser(@PathVariable String id) {
        userManagementService.deactivateUser(id);
        return ResponseEntity.ok(Map.of("message", "User deactivated successfully"));
    }

    @PutMapping("/verify/{id}")
    public ResponseEntity<Map<String, String>> verifyUser(@PathVariable String id) {
        userManagementService.verifyUser(id);
        return ResponseEntity.ok(Map.of("message", "User verified successfully"));
    }

    @PutMapping("/unverify/{id}")
    public ResponseEntity<Map<String, String>> unverifyUser(@PathVariable String id) {
        userManagementService.unverifyUser(id);
        return ResponseEntity.ok(Map.of("message", "User unverified successfully"));
    }

    @PostMapping("/search")
    public ResponseEntity<List<UserDto>> searchUsers(@RequestBody UserSearchRequest request) {
        return ResponseEntity.ok(userManagementService.searchUsers(request));
    }

    @PutMapping("/update-password/{id}")
    public ResponseEntity<Map<String, String>> updatePassword(
            @PathVariable String id,
            @Valid @RequestBody PasswordUpdateRequest request) {
        userManagementService.updatePassword(id, request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    @PutMapping("/reset-password/{id}")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable String id) {
        userManagementService.resetPassword(id);
        return ResponseEntity.ok(Map.of("message", "Password reset started. The user has been emailed a link to choose a new password."));
    }

    /**
     * Change User Role with proper validation
     */
    @PutMapping("/change-role/{id}")
    public ResponseEntity<Map<String, String>> changeUserRole(
            @PathVariable String id,
            @Valid @RequestBody RoleChangeRequest request) {
        
        userManagementService.changeUserRole(id, request.getRole());
        return ResponseEntity.ok(Map.of("message", "User role updated successfully"));
    }

    @GetMapping("/active")
    public ResponseEntity<List<UserDto>> getActiveUsers() {
        return ResponseEntity.ok(userManagementService.getActiveUsers());
    }

    @GetMapping("/archived")
    public ResponseEntity<List<UserDto>> getArchivedUsers() {
        return ResponseEntity.ok(userManagementService.getArchivedUsers());
    }

    @GetMapping("/exists/email/{email}")
    public ResponseEntity<Boolean> checkEmailExists(@PathVariable String email) {
        return ResponseEntity.ok(userManagementService.existsByEmail(email));
    }

    @GetMapping("/exists/username/{username}")
    public ResponseEntity<Boolean> checkUsernameExists(@PathVariable String username) {
        return ResponseEntity.ok(userManagementService.existsByUsername(username));
    }

    // ==================== Inner DTO Classes ====================

    public static class PasswordUpdateRequest {
        @jakarta.validation.constraints.NotBlank(message = "New password is required")
        @jakarta.validation.constraints.Size(min = 8, message = "Password must be at least 8 characters")
        private String newPassword;

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    public static class RoleChangeRequest {
        @jakarta.validation.constraints.NotBlank(message = "Role is required")
        private String role;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}