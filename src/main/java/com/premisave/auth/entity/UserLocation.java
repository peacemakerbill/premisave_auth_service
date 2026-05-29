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
@Document(collection = "user_locations")
@CompoundIndexes({
    @CompoundIndex(name = "user_active_idx", def = "{'user.$id': 1, 'isCurrent': 1}"),
    @CompoundIndex(name = "user_timestamp_idx", def = "{'user.$id': 1, 'createdAt': -1}")
})
public class UserLocation {

    @Id
    private String id;

    @DocumentReference
    private User user;

    private Double latitude;
    private Double longitude;

    private String locationName;   // e.g., "Westlands, Nairobi"
    private String address;
    private String country;
    private String city;

    private boolean isCurrent = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}