package com.premisave.auth.enums;

import java.util.Locale;
import java.util.Optional;

/** What a contact message is about. The app sends the code; the label is what the team sees. */
public enum ContactTopic {
    GENERAL("General question"),
    ACCOUNT("Account and sign in"),
    APPLICATION("Home owner application"),
    PAYMENTS("Bookings and payments"),
    SECURITY("Security concern"),
    FEEDBACK("Feedback or an idea");

    private final String label;

    ContactTopic(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Accepts the code (GENERAL) or the label (General question), in any letter case. */
    public static Optional<ContactTopic> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String wanted = value.trim().toUpperCase(Locale.ROOT);
        for (ContactTopic topic : values()) {
            if (topic.name().equals(wanted) || topic.label.toUpperCase(Locale.ROOT).equals(wanted)) {
                return Optional.of(topic);
            }
        }
        return Optional.empty();
    }
}
