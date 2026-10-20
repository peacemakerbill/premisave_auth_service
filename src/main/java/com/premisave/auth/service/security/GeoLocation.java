package com.premisave.auth.service.security;

/** Approximate place derived from an IP address. Any part can be missing. */
public record GeoLocation(String country, String region, String city) {

    private static final GeoLocation NONE = new GeoLocation(null, null, null);

    public static GeoLocation unavailable() {
        return NONE;
    }

    public boolean isAvailable() {
        return country != null || region != null || city != null;
    }

    /** "Nairobi, Nairobi County, Kenya (approximate)" or "Unavailable". */
    public String describe() {
        if (!isAvailable()) {
            return "Unavailable";
        }
        StringBuilder out = new StringBuilder();
        for (String part : new String[]{city, region, country}) {
            if (part != null && !part.isBlank()) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(part);
            }
        }
        return out + " (approximate)";
    }
}
