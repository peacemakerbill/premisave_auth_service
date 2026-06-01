package com.premisave.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhoIViewedResponse {
    private String targetId;
    private String targetName;
    private String targetProfilePicture;
    private String targetUsername;
    private LocalDateTime viewedAt;
    private String deviceType;
    private String source;
}