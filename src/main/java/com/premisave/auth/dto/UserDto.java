package com.premisave.auth.dto;

import com.premisave.auth.enums.Language;
import com.premisave.auth.enums.Role;
import lombok.Data;

@Data
public class UserDto {
    private String id;
    private String username; 
    private String firstName;
    private String middleName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String address1;
    private String address2;
    private String country;
    private Language language;
    private String profilePictureUrl;
    private Role role;
    private boolean active;
    private boolean verified;
    private boolean archived;
    private String password;           // Only for updates (should be null in responses)
}