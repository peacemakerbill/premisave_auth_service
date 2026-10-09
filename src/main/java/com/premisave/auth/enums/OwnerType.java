package com.premisave.auth.enums;

public enum OwnerType {
    INDIVIDUAL("Individual landlord", "You own the property in your own name."),
    COMPANY("Company or organisation", "The property is owned by a registered company, cooperative, trust or other organisation."),
    PROPERTY_MANAGER("Property manager", "You manage property on behalf of one or more owners.");

    private final String label;
    private final String description;

    OwnerType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
}