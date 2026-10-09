package com.premisave.auth.enums;

public enum DocumentStatus {
    PENDING("Waiting for review"),
    VERIFIED("Verified"),
    REJECTED("Needs replacing");

    private final String label;

    DocumentStatus(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}