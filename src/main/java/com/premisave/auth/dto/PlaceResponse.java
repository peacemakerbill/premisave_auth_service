package com.premisave.auth.dto;

/** A point on the map with the address parts known for it. Any part can be null. */
public record PlaceResponse(
        double latitude,
        double longitude,
        String name,
        String houseNumber,
        String street,
        String district,
        String city,
        String state,
        String postcode,
        String country,
        String countryCode) {
}
