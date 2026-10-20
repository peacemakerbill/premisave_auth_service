package com.premisave.auth.dto;

import lombok.Data;

@Data
public class AuthResponse {

    private String token;
    private String role;
    private String refreshToken;

    /** Seconds until the access token expires, so clients can refresh ahead of time. Null for older flows. */
    private Long expiresIn;

    /**
     * New remembered-device token. Only present when a native app asked for it with the
     * X-Client-Platform: mobile header; browsers receive it as an HttpOnly cookie instead and
     * JavaScript never sees it. Apps send it back as the X-Device-Token header.
     */
    private String deviceToken;

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
