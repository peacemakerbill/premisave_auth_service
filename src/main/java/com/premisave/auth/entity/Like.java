package com.premisave.auth.entity;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;

@Data
@Document(collection = "likes")
@CompoundIndexes({
    @CompoundIndex(name = "user_target_idx", def = "{'user.$id': 1, 'targetId': 1}", unique = true)
})
public class Like {

    @Id
    private String id;

    @DocumentReference
    private User user;

    private String targetId;

    @CreatedDate
    private LocalDateTime createdAt;
}