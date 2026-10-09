package com.premisave.auth.enums;

public enum PropertyType {
    APARTMENT_BLOCK("Apartment block"),
    MAISONETTE_TOWNHOUSE("Maisonettes or townhouses"),
    STANDALONE_HOUSE("Standalone houses"),
    BEDSITTER_STUDIO_UNITS("Bedsitters and studios"),
    COMMERCIAL_SHOPS_OFFICES("Shops and offices"),
    HOSTEL_STUDENT_HOUSING("Hostels and student housing"),
    SERVICED_SHORT_STAY("Serviced or short stay units"),
    LAND_PLOTS("Land and plots"),
    MIXED_USE("Mixed use"),
    OTHER("Other");

    private final String label;

    PropertyType(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}