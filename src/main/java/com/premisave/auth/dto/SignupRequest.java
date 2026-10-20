package com.premisave.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Registration body. There is deliberately no role field: every self-registered account starts
 * as CLIENT, and roles are only ever assigned on the server (administrator configuration,
 * the home owner application review, or an administrator). A "role" sent by a client is ignored.
 */
@Data
public class SignupRequest {
    /** Optional. Leave it out and one is generated from the name; it can be changed later in the profile. */
    @Pattern(regexp = "^$|^[a-zA-Z0-9_.-]{3,50}$",
            message = "Username must be 3 to 50 letters, digits, dots, underscores or hyphens")
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
}
