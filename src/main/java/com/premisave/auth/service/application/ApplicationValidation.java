package com.premisave.auth.service.application;

import com.premisave.auth.exception.ApiException;

import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

/** Small formatting and sanity rules shared by the application services. */
final class ApplicationValidation {

    private static final Pattern KENYAN_MOBILE = Pattern.compile("^254[17][0-9]{8}$");
    private static final Pattern ANY_PHONE_DIGITS = Pattern.compile("^[0-9]{9,15}$");

    private ApplicationValidation() {
    }

    /** Applies the "null leaves it alone, blank clears it" rule used by partial updates. */
    static String merge(String incoming, String current) {
        if (incoming == null) {
            return current;
        }
        String trimmed = incoming.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Strips spaces, dashes and a leading plus, and turns Kenyan local numbers
     * (0712345678, 712345678) into the 2547... form. Other countries are kept as digits.
     */
    static String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.matches("^0[17][0-9]{8}$")) {
            digits = "254" + digits.substring(1);
        } else if (digits.matches("^[17][0-9]{8}$")) {
            digits = "254" + digits;
        }
        if (!ANY_PHONE_DIGITS.matcher(digits).matches()) {
            throw ApiException.badRequest("Phone number looks invalid: " + raw);
        }
        return digits;
    }

    static String requireKenyanMobile(String raw, String label) {
        String normalized = normalizePhone(raw);
        if (normalized == null || !KENYAN_MOBILE.matcher(normalized).matches()) {
            throw ApiException.badRequest(label + " must be a valid Kenyan mobile number, for example 0712345678");
        }
        return normalized;
    }

    static boolean isAdult(LocalDate dateOfBirth) {
        return dateOfBirth != null && Period.between(dateOfBirth, LocalDate.now()).getYears() >= 18;
    }

    static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}