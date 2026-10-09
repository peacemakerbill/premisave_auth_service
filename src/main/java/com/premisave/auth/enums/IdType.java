package com.premisave.auth.enums;

/** The kind of government photo ID the applicant is giving us the number of. */
public enum IdType {
    NATIONAL_ID("National ID card"),
    PASSPORT("Passport"),
    DRIVING_LICENCE("Driving licence"),
    RESIDENCE_PERMIT("Residence permit");

    private final String label;

    IdType(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}