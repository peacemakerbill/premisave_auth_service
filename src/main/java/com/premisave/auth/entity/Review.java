package com.premisave.auth.entity;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;

@Data
@Document(collection = "reviews")
@CompoundIndexes({
    @CompoundIndex(name = "user_target_idx", def = "{'user.$id': 1, 'targetId': 1}"),
    @CompoundIndex(name = "target_rating_idx", def = "{'targetId': 1, 'rating': 1}")
})
public class Review {

    @Id
    private String id;

    @DocumentReference
    private User user;

    private String targetId;

    private int rating; // 1-5 stars
    private String comment;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}