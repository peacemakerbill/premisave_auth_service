package com.premisave.auth.entity;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;

@Data
@Document(collection = "likes")
public class Like {

    @Id
    private String id;

    @DocumentReference
    private User user;

    private String targetId;

    // === AUDIT FIELDS ===
    @CreatedDate
    private LocalDateTime createdAt;
}