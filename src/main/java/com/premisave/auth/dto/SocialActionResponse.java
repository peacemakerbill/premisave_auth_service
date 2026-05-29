package com.premisave.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocialActionResponse {

    private boolean success;
    private String message;
    private String action; // LIKE, UNLIKE, FOLLOW, UNFOLLOW, REVIEW

    public static SocialActionResponse success(String action, String message) {
        return new SocialActionResponse(true, message, action);
    }

    public static SocialActionResponse error(String action, String message) {
        return new SocialActionResponse(false, message, action);
    }
}