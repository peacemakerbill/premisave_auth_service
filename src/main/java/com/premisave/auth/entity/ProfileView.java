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
@Document(collection = "profile_views")
@CompoundIndexes({
    @CompoundIndex(name = "viewer_target_idx", def = "{'viewer.$id': 1, 'target.$id': 1}"),
    @CompoundIndex(name = "target_viewed_idx", def = "{'target.$id': 1, 'viewedAt': -1}"),
    @CompoundIndex(name = "viewer_date_idx", def = "{'viewer.$id': 1, 'viewedAt': -1}")
})
public class ProfileView {

    @Id
    private String id;

    @DocumentReference
    private User viewer;

    @DocumentReference
    private User target;

    @CreatedDate
    private LocalDateTime viewedAt;

    private String ipAddress;
    private String userAgent;
    private String deviceType;
    private String source;

    private int viewDurationSeconds;
    private boolean isAnonymous;
}