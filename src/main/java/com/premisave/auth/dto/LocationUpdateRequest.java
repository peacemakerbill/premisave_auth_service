package com.premisave.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LocationUpdateRequest {

    @NotNull(message = "Latitude is required")
    @Min(-90) @Max(90)
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @Min(-180) @Max(180)
    private Double longitude;

    private String locationName;
    private String address;
    private String country;
    private String city;
}