package com.premisave.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileViewResponse {
    private String id;
    private String viewerId;
    private String viewerName;
    private String viewerProfilePicture;
    private String targetId;
    private LocalDateTime viewedAt;
    private String source;
    private String deviceType;
    private String message;
}