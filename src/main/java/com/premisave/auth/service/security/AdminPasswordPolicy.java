package com.premisave.auth.service.security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rules for a password that comes from configuration. A configured password is typed once by an
 * operator and used for the most powerful accounts, so the rules are stricter than signup's.
 *
 * The result lists problems in words and never repeats any part of the password.
 */
public final class AdminPasswordPolicy {

    /** BCrypt only uses the first 72 bytes; longer input would silently ignore the rest. */
    static final int MAX_BYTES = 72;
    /** Floor for the configurable minimum, so a mistake in the config cannot allow short passwords. */
    static final int ABSOLUTE_MIN_LENGTH = 8;

    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "passw0rd", "qwerty", "qwertyuiop", "letmein",
            "welcome", "welcome1", "admin", "admin123", "administrator", "changeme", "iloveyou",
            "123456", "12345678", "123456789", "1234567890", "premisave", "temp123", "abc123");

    private AdminPasswordPolicy() {
    }

    /**
     * @param password  the candidate
     * @param minLength configured minimum (raised to 8 if lower)
     * @param email     the account email, which the password must not contain
     * @param username  the handle, may be empty
     * @return an empty list when the password is acceptable
     */
    public static List<String> problems(String password, int minLength, String email, String username) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.isBlank()) {
            problems.add("is missing");
            return problems;
        }
        int min = Math.max(minLength, ABSOLUTE_MIN_LENGTH);
        if (!password.equals(password.strip())) {
            problems.add("starts or ends with a space");
        }
        if (password.codePointCount(0, password.length()) < min) {
            problems.add("is shorter than " + min + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            problems.add("is longer than " + MAX_BYTES + " bytes");
        }

        int classes = 0;
        classes += password.chars().anyMatch(Character::isLowerCase) ? 1 : 0;
        classes += password.chars().anyMatch(Character::isUpperCase) ? 1 : 0;
        classes += password.chars().anyMatch(Character::isDigit) ? 1 : 0;
        classes += password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)) ? 1 : 0;
        if (classes < 2) {
            problems.add("needs at least two of: lower case, upper case, digits, symbols");
        }

        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            problems.add("is a well-known password");
        }
        if (password.chars().distinct().count() < 5) {
            problems.add("uses too few different characters");
        }
        if (contains(lower, email) || contains(lower, localPart(email)) || contains(lower, username)) {
            problems.add("contains the email address or username");
        }
        return problems;
    }

    private static boolean contains(String lowerPassword, String part) {
        if (part == null) {
            return false;
        }
        String p = part.trim().toLowerCase(Locale.ROOT);
        return p.length() >= 4 && lowerPassword.contains(p);
    }

    private static String localPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}