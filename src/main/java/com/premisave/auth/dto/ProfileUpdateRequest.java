package com.premisave.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Username can only contain letters, numbers, dots, underscores, and hyphens")
    private String username;
    
    @Size(max = 100, message = "First name must be less than 100 characters")
    private String firstName;
    
    @Size(max = 100, message = "Middle name must be less than 100 characters")
    private String middleName;
    
    @Size(max = 100, message = "Last name must be less than 100 characters")
    private String lastName;
    
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phoneNumber;
    
    @Size(max = 255, message = "Address 1 must be less than 255 characters")
    private String address1;
    
    @Size(max = 255, message = "Address 2 must be less than 255 characters")
    private String address2;
    
    @Size(max = 100, message = "Country must be less than 100 characters")
    private String country;
    
    private String language;
}