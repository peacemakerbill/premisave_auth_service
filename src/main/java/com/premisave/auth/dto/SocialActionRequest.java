package com.premisave.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SocialActionRequest {

    // Required for Like, Follow, Create Review
    private String targetId;

    // Required for Edit & Delete Review
    private String reviewId;

    // For reviews only
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    @Size(max = 1000, message = "Comment cannot exceed 1000 characters")
    private String comment;
}