package com.premisave.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OAuthRequest {

    @NotBlank(message = "Provider is required")
    private String provider; // "google" or "facebook"

    @NotBlank(message = "Token is required")
    private String token;    // ID token (Google) or access token (Facebook)
}