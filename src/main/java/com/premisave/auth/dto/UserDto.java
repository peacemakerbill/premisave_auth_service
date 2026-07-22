package com.premisave.auth.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.premisave.auth.enums.Language;
import com.premisave.auth.enums.Role;
import lombok.Data;

@Data
@JsonPropertyOrder({
    "id", "username", "email",
    "firstName", "middleName", "lastName", "fullName",
    "phoneNumber", "country", "address1", "address2",
    "language", "profilePictureUrl",
    "role",
    "active", "verified", "archived",
    "password"
})
public class UserDto {

    // Identification
    private String id;
    private String username;
    private String email;

    // Personal Information
    private String firstName;
    private String middleName;
    private String lastName;

    // Contact & Location
    private String phoneNumber;
    private String country;
    private String address1;
    private String address2;

    // Preferences
    private Language language;
    private String profilePictureUrl;

    // Authorization
    private Role role;

    // Account Status (boxed Boolean so null = "not provided" vs false = "explicitly set to false")
    private Boolean active;
    private Boolean verified;
    private Boolean archived;

    // Security (should always be null in responses)
    private String password;

    /**
     * Computed convenience field for cross-service consumers (e.g.
     * property-service's own UserDto, deserialized from /profile/me) that
     * expect a single fullName rather than firstName/middleName/lastName.
     * No backing field — Jackson serializes this as "fullName" automatically
     * because it's a public no-arg getter following bean convention.
     */
    public String getFullName() {
        StringBuilder sb = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            sb.append(firstName.trim());
        }
        if (middleName != null && !middleName.isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(middleName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(lastName.trim());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}