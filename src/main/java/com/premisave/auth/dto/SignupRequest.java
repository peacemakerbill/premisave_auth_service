package com.premisave.auth.dto;

import com.premisave.auth.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SignupRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String firstName;

    private String middleName;

    @NotBlank
    private String lastName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String phoneNumber;

    private String address1;

    private String address2;

    private String country;

    private String language = "ENGLISH";

    @NotBlank
    private String password;

    private Role role = Role.CLIENT;
}