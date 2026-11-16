package com.premisave.auth.dto;

/** One address suggestion. The id is exchanged for the full place with GET /places/details. */
public record PlaceSuggestionResponse(String id, String title, String subtitle) {
}
