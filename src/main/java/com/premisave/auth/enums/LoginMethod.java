package com.premisave.auth.enums;

/** How a session was started. */
public enum LoginMethod {
    PASSWORD("password"),
    SIGNUP("new account registration"),
    INVITATION("administrator invitation"),
    GOOGLE("Google"),
    FACEBOOK("Facebook"),
    GITHUB("GitHub");

    private final String label;

    LoginMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True when the user proved their identity with a password during this sign-in. */
    public boolean isPasswordBased() {
        return this == PASSWORD || this == SIGNUP || this == INVITATION;
    }

    public static LoginMethod fromProvider(String provider) {
        if (provider == null) {
            return PASSWORD;
        }
        return switch (provider.toLowerCase()) {
            case "google" -> GOOGLE;
            case "facebook" -> FACEBOOK;
            case "github" -> GITHUB;
            default -> PASSWORD;
        };
    }
}
