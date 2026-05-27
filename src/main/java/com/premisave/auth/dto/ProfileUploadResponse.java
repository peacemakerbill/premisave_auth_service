package com.premisave.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUploadResponse {

    private String message;
    private String profilePictureUrl;
    private boolean success;

    /**
     * Constructor for success response
     */
    public ProfileUploadResponse(String message, String profilePictureUrl) {
        this.message = message;
        this.profilePictureUrl = profilePictureUrl;
        this.success = true;
    }

    /**
     * Constructor for error response
     */
    public ProfileUploadResponse(String message) {
        this.message = message;
        this.profilePictureUrl = null;
        this.success = false;
    }
}