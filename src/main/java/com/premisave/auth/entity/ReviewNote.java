package com.premisave.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Internal remark between staff. Never shown to the applicant. */
@Data
public class ReviewNote {

    private String id = UUID.randomUUID().toString();
    private String authorId;
    private String authorName;
    private String authorRole;
    private String text;
    private LocalDateTime at = LocalDateTime.now();
}