package com.premisave.auth.service.application;

import com.premisave.auth.entity.User;
import com.premisave.auth.enums.Role;
import com.premisave.auth.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/** Reads the signed-in user, and knows which roles count as staff. */
@Component
public class CurrentUser {

    /** Roles that may review applications. */
    public static final Set<Role> STAFF_ROLES = EnumSet.of(Role.ADMIN, Role.OPERATIONS, Role.FINANCE, Role.SUPPORT);

    public User require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        }
        return user;
    }

    public User requireStaff() {
        User user = require();
        if (!isStaff(user)) {
            throw ApiException.forbidden("Only Premisave staff can do this.");
        }
        return user;
    }

    public User requireAdmin() {
        User user = require();
        if (user.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Only an administrator can do this.");
        }
        return user;
    }

    public static boolean isStaff(User user) {
        return user != null && user.getRole() != null && STAFF_ROLES.contains(user.getRole());
    }

    public static String fullName(User user) {
        StringBuilder name = new StringBuilder();
        append(name, user.getFirstName());
        append(name, user.getMiddleName());
        append(name, user.getLastName());
        return name.length() > 0 ? name.toString() : user.getEmail();
    }

    private static void append(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part.trim());
        }
    }
}