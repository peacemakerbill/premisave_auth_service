package com.premisave.auth.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A review as the API returns it. The reviewer's details are built here, from the users collection, so clients
 * never have to look the reviewer up again. userId and targetId are always real user ids, never emails.
 * When the reviewer's account no longer exists the name, email and picture are null and userId is kept.
 */
@Data
@JsonPropertyOrder({
    "id", "userId", "reviewerName", "reviewerEmail", "reviewerProfilePicture",
    "targetId", "rating", "comment", "createdAt", "updatedAt"
})
public class ReviewResponse {

    private String id;

    /** The reviewer's user id. */
    private String userId;
    private String reviewerName;
    private String reviewerEmail;
    private String reviewerProfilePicture;

    /** The id of the user the review is about. */
    private String targetId;

    private int rating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}