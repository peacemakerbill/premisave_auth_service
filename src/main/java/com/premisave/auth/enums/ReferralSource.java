package com.premisave.auth.enums;

public enum ReferralSource {
    SOCIAL_MEDIA("Social media"),
    SEARCH_ENGINE("Search engine"),
    FRIEND_OR_FAMILY("Friend or family"),
    EXISTING_HOME_OWNER("Another Home Owner on Premisave"),
    TENANT("A tenant"),
    PROPERTY_AGENT("Property agent"),
    EVENT_OR_ADVERT("Event or advert"),
    OTHER("Other");

    private final String label;

    ReferralSource(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}