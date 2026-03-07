package com.premisave.auth.dto;

import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private String role;
    private String redirectUrl;
    private String refreshToken; // Optional: if you want to return refresh token
    
    // Constructor without refresh token (backward compatible)
    public AuthResponse(String token, String role, String redirectUrl) {
        this.token = token;
        this.role = role;
        this.redirectUrl = redirectUrl;
    }
    
    // Constructor with refresh token
    public AuthResponse(String token, String role, String redirectUrl, String refreshToken) {
        this.token = token;
        this.role = role;
        this.redirectUrl = redirectUrl;
        this.refreshToken = refreshToken;
    }
    
    // Default constructor for Jackson
    public AuthResponse() {}
}