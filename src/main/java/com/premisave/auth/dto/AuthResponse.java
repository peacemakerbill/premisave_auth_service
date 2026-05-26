package com.premisave.auth.dto;

import lombok.Data;

@Data
public class AuthResponse {

    private String token;
    private String role;
    private String refreshToken; // Optional - can be included in future if needed

    /**
     * Constructor for successful authentication response
     */
    public AuthResponse(String token, String role) {
        this.token = token;
        this.role = role;
    }

    /**
     * Constructor with refresh token support
     */
    public AuthResponse(String token, String role, String refreshToken) {
        this.token = token;
        this.role = role;
        this.refreshToken = refreshToken;
    }

    /**
     * Default constructor for Jackson deserialization
     */
    public AuthResponse() {}
}