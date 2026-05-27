package com.premisave.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogoutResponse {

    private String message;
    private boolean success;

    /**
     * Constructor for success response
     */
    public LogoutResponse(String message) {
        this.message = message;
        this.success = true;
    }
}