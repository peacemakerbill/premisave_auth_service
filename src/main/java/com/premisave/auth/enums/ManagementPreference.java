package com.premisave.auth.enums;

public enum ManagementPreference {
    SELF_MANAGED("I manage my properties myself"),
    THROUGH_AGENT("I use an agent or caretaker"),
    UNSURE("I am not sure yet");

    private final String label;

    ManagementPreference(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}