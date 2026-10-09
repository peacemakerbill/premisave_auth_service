package com.premisave.auth.service.application;

import com.premisave.auth.exception.ApiException;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Small formatting and sanity rules shared by the application services. */
final class ApplicationValidation {

    /** International format (E.164): a plus, then 7 to 15 digits, not starting with 0. */
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{6,14}$");

    private static final Map<String, String> BY_ALPHA3 = new HashMap<>();
    private static final Map<String, String> BY_NAME = new HashMap<>();

    static {
        for (String code : Locale.getISOCountries()) {
            Locale locale = Locale.of("", code);
            BY_ALPHA3.put(locale.getISO3Country().toUpperCase(Locale.ROOT), code);
            BY_NAME.put(locale.getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT), code);
        }
        // Common alternative names that differ from the official English name
        BY_NAME.put("usa", "US");
        BY_NAME.put("united states of america", "US");
        BY_NAME.put("uk", "GB");
        BY_NAME.put("great britain", "GB");
        BY_NAME.put("england", "GB");
        BY_NAME.put("uae", "AE");
        BY_NAME.put("south korea", "KR");
        BY_NAME.put("tanzania", "TZ");
        BY_NAME.put("russia", "RU");
        BY_NAME.put("ivory coast", "CI");
    }

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
     * Turns a phone number into international format. Spaces, dashes, dots and
     * brackets are removed and a leading 00 becomes +. A number must carry its country
     * code (+254712345678, +14155552671, +447911123456) because a local number alone
     * does not say which country it belongs to.
     */
    static String normalizePhone(String raw, String label) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[\\s().\\-]", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2);
        }
        if (!E164.matcher(cleaned).matches()) {
            throw ApiException.badRequest(label + " must include the country code, for example +254712345678");
        }
        return cleaned;
    }

    static String normalizePhone(String raw) {
        return normalizePhone(raw, "Phone number");
    }

    /**
     * Accepts a country as a two or three letter ISO code or an English name and
     * returns the two letter ISO code. Blank means "clear it".
     */
    static String countryCode(String raw, String label) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String value = raw.trim();
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.length() == 2 && Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2).contains(upper)) {
            return upper;
        }
        if (upper.length() == 3 && BY_ALPHA3.containsKey(upper)) {
            return BY_ALPHA3.get(upper);
        }
        String byName = BY_NAME.get(value.toLowerCase(Locale.ROOT));
        if (byName != null) {
            return byName;
        }
        throw ApiException.badRequest(label + " is not a recognised country. Choose one from the list.");
    }

    /** Like countryCode, but returns null instead of failing. Used to pre-fill from a free text profile. */
    static String countryCodeOrNull(String raw) {
        try {
            return countryCode(raw, "Country");
        } catch (ApiException e) {
            return null;
        }
    }

    static boolean isAdult(LocalDate dateOfBirth) {
        return dateOfBirth != null && Period.between(dateOfBirth, LocalDate.now()).getYears() >= 18;
    }

    static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}