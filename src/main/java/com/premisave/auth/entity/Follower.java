package com.premisave.auth.entity;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;

@Data
@Document(collection = "followers")
public class Follower {

    @Id
    private String id;

    @DocumentReference
    private User user;

    @DocumentReference
    private User follower;

    // === AUDIT FIELDS ===
    @CreatedDate
    private LocalDateTime createdAt;
}