package com.premisave.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationResponse {

    private String id;
    
    private Double latitude;
    private Double longitude;
    
    private String locationName;
    private String address;
    private String country;
    private String city;
    
    private boolean isCurrent;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Optional: Constructor for quick creation
    public LocationResponse(Double latitude, Double longitude, String locationName, 
                           String address, String country, String city, boolean isCurrent) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationName = locationName;
        this.address = address;
        this.country = country;
        this.city = city;
        this.isCurrent = isCurrent;
    }
}