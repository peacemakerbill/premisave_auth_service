package com.premisave.auth.service.security;

import com.premisave.auth.config.SecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which email addresses are administrators by configuration.
 *
 * Both lists come from INITIAL_ADMIN_EMAILS and AUTO_ADMIN_EMAILS. Entries are trimmed,
 * lower-cased, validated and de-duplicated; invalid entries are skipped and logged in a
 * masked form. The lists are read at startup, so changing them needs a restart.
 */
@Slf4j
@Component
public class AdminEmailPolicy {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]{2,}$");
    private static final int MAX_LENGTH = 254;

    private final Set<String> initialAdmins;
    private final Set<String> autoAdmins;

    public AdminEmailPolicy(SecurityProperties properties) {
        this.initialAdmins = parse(properties.getInitialAdminEmails(), "INITIAL_ADMIN_EMAILS");
        this.autoAdmins = parse(properties.getAutoAdminEmails(), "AUTO_ADMIN_EMAILS");
        log.info("Admin email policy loaded: {} initial, {} auto-assign", initialAdmins.size(), autoAdmins.size());
    }

    /** Emails provisioned as administrators at startup, in configuration order. */
    public Set<String> initialAdminEmails() {
        return initialAdmins;
    }

    /** True if this verified email may hold the ADMIN role by configuration (either list). */
    public boolean isConfiguredAdmin(String email) {
        String normalized = normalize(email);
        return normalized != null && (autoAdmins.contains(normalized) || initialAdmins.contains(normalized));
    }

    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        String value = email.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > MAX_LENGTH || !EMAIL.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    static Set<String> parse(String raw, String source) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        List<String> rejected = new ArrayList<>();
        for (String entry : raw.split("[,;\\s]+")) {
            if (entry.isBlank()) {
                continue;
            }
            String normalized = normalize(entry);
            if (normalized == null) {
                rejected.add(mask(entry));
            } else {
                result.add(normalized);
            }
        }
        if (!rejected.isEmpty()) {
            log.warn("{} contains {} invalid entr{} that will be ignored: {}", source, rejected.size(),
                    rejected.size() == 1 ? "y" : "ies", rejected);
        }
        return Collections.unmodifiableSet(result);
    }

    /** a***@example.com, enough to find the typo without printing the address. */
    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "(empty)";
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0) {
            return trimmed.length() <= 2 ? "***" : trimmed.charAt(0) + "***";
        }
        return trimmed.charAt(0) + "***" + trimmed.substring(at);
    }
}
