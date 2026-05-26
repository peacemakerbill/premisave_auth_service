package com.premisave.auth.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.premisave.auth.enums.Language;
import com.premisave.auth.enums.Role;
import lombok.Data;

@Data
@JsonPropertyOrder({
    "id", "username", "email",
    "firstName", "middleName", "lastName",
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

    // Account Status
    private boolean active;
    private boolean verified;
    private boolean archived;

    // Security (should always be null in responses)
    private String password;
}