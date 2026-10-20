package com.premisave.auth.service.security;

import com.premisave.auth.config.AdminsProperties;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.Language;
import com.premisave.auth.enums.Role;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.enums.SecuritySeverity;
import com.premisave.auth.repository.UserRepository;
import com.premisave.auth.service.UsernameService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Creates the administrators configured through ADMIN1_*, ADMIN2_* and ADMIN3_*.
 *
 * Safety rules, all enforced here and nowhere else:
 *   - The password is validated (length, variety, no well-known or email-based passwords) and
 *     stored only as a BCrypt hash. It is never logged, audited, returned or put in an exception.
 *   - An account is only ever CREATED by this class. An existing account's password, names,
 *     username, status and role are never overwritten, so editing the environment cannot take
 *     over or reset somebody's account.
 *   - An existing non-admin account is promoted only when ADMINS_PROMOTE_EXISTING is true and the
 *     account is verified, active and not archived. By default it is left alone and a warning
 *     is logged.
 *   - Nobody is ever demoted or deactivated by configuration.
 *   - Invalid slots are skipped (or stop the startup in strict mode) and the log names the slot
 *     and the problem, never the value.
 *   - The plain-text password is cleared from the in-memory properties after it has been used.
 */
@Slf4j
@Service
public class ConfiguredAdminService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,29}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]{2,}$");
    private static final int MAX_NAME_LENGTH = 60;
    private static final int MAX_EMAIL_LENGTH = 254;

    public enum Outcome { UNUSED, CREATED, PROMOTED, ALREADY_ADMIN, LEFT_UNCHANGED, INVALID, FAILED }

    /** What happened to one slot. Contains no secret and no full email address. */
    public record Result(String slot, Outcome outcome, String detail) {
    }

    /** A slot that passed validation, with normalized values. */
    record Valid(String slot, String email, String username, String firstName, String middleName, String lastName) {
    }

    /** Validation outcome: either the normalized slot or a list of problems. */
    record Validation(Valid valid, List<String> problems) {
        boolean ok() {
            return valid != null;
        }
    }

    private final AdminsProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminAccessService adminAccess;
    private final UsernameService usernameService;
    private final SecurityAuditService audit;
    private final SecurityNotificationService notifications;

    public ConfiguredAdminService(AdminsProperties properties, UserRepository users, PasswordEncoder passwordEncoder,
                                  AdminAccessService adminAccess, UsernameService usernameService,
                                  SecurityAuditService audit, SecurityNotificationService notifications) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminAccess = adminAccess;
        this.usernameService = usernameService;
        this.audit = audit;
        this.notifications = notifications;
    }

    /**
     * Provisions every configured slot.
     *
     * @throws IllegalStateException in strict mode when any used slot is invalid; nothing is created then
     */
    public List<Result> provisionAll() {
        List<AdminsProperties.NamedSlot> slots = properties.slots();
        Result[] results = new Result[slots.size()];
        List<Validation> validations = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenUsernames = new HashSet<>();

        try {
            for (int i = 0; i < slots.size(); i++) {
                AdminsProperties.NamedSlot named = slots.get(i);
                if (named.slot().isEmpty()) {
                    validations.add(null);
                    results[i] = new Result(named.name(), Outcome.UNUSED, "not configured");
                    continue;
                }
                Validation validation = validate(named.name(), named.slot(), properties.getMinPasswordLength());
                if (validation.ok()) {
                    List<String> duplicates = new ArrayList<>();
                    if (!seenEmails.add(validation.valid().email())) {
                        duplicates.add("email is already used by an earlier slot");
                    }
                    if (!validation.valid().username().isEmpty() && !seenUsernames.add(validation.valid().username())) {
                        duplicates.add("username is already used by an earlier slot");
                    }
                    if (!duplicates.isEmpty()) {
                        validation = new Validation(null, duplicates);
                    }
                }
                validations.add(validation);
            }

            if (properties.isStrict()) {
                List<String> failures = new ArrayList<>();
                for (int i = 0; i < slots.size(); i++) {
                    Validation v = validations.get(i);
                    if (v != null && !v.ok()) {
                        failures.add(slots.get(i).name() + " " + String.join("; ", v.problems()));
                    }
                }
                if (!failures.isEmpty()) {
                    throw new IllegalStateException("Administrator configuration is invalid and ADMINS_STRICT is on: "
                            + String.join(" | ", failures));
                }
            }

            for (int i = 0; i < slots.size(); i++) {
                Validation v = validations.get(i);
                if (v == null) {
                    continue; // unused slot, result already recorded
                }
                String name = slots.get(i).name();
                if (!v.ok()) {
                    log.error("{} is not used: {}", name, String.join("; ", v.problems()));
                    results[i] = new Result(name, Outcome.INVALID, String.join("; ", v.problems()));
                    continue;
                }
                results[i] = provisionOne(v.valid(), slots.get(i).slot().getPassword());
            }
        } finally {
            // The hash is all that is needed from here on; do not keep the plain text in memory.
            for (AdminsProperties.NamedSlot named : slots) {
                named.slot().setPassword("");
            }
        }
        return List.of(results);
    }

    private Result provisionOne(Valid v, String password) {
        String masked = AdminEmailPolicy.mask(v.email());
        try {
            User existing = adminAccess.findByEmailIgnoreCase(v.email());
            if (existing != null) {
                return handleExisting(v, existing, masked);
            }

            if (!v.username().isEmpty() && usernameService.isTaken(v.username())) {
                log.error("{} is not used: the username is already taken by another account.", v.slot());
                return new Result(v.slot(), Outcome.INVALID, "username is already taken");
            }

            User user = new User();
            user.setEmail(v.email());
            user.setFirstName(v.firstName());
            user.setMiddleName(v.middleName().isEmpty() ? null : v.middleName());
            user.setLastName(v.lastName().isEmpty() ? null : v.lastName());
            user.setRole(Role.ADMIN);
            user.setActive(true);
            // The operator who controls the environment vouches for this address, so there is no
            // verification mail to wait for. An invitation would only be needed for addresses nobody
            // vouched for, and that path is still INITIAL_ADMIN_EMAILS.
            user.setVerified(true);
            user.setArchived(false);
            user.setLanguage(Language.ENGLISH);
            user.setPassword(passwordEncoder.encode(password));
            if (!v.username().isEmpty()) {
                user.setUsername(v.username());
                user.setUsernameGenerated(false);
            }
            user = users.save(user);

            if (v.username().isEmpty()) {
                try {
                    usernameService.assignInBackground(user.getId());
                } catch (RuntimeException e) {
                    log.debug("Username assignment skipped: {}", e.getClass().getSimpleName());
                }
            }

            audit.event(SecurityEventType.ADMIN_PROVISIONED)
                    .severity(SecuritySeverity.WARNING)
                    .user(user.getId())
                    .actor("SYSTEM")
                    .reason("Administrator account created from " + v.slot() + " configuration")
                    .detail("source", "CONFIG")
                    .detail("slot", v.slot())
                    .write();
            log.info("{} created: administrator {} can sign in with the configured password. Rotate it after first sign-in.",
                    v.slot(), masked);
            notifyCreated(v, user, masked);
            return new Result(v.slot(), Outcome.CREATED, masked);
        } catch (RuntimeException e) {
            // Class name only: exception messages from drivers can echo documents.
            log.error("{} could not be provisioned: {}", v.slot(), e.getClass().getSimpleName());
            return new Result(v.slot(), Outcome.FAILED, e.getClass().getSimpleName());
        }
    }

    /**
     * Tells the new administrator that the account exists. The mail never contains the password.
     * A mail failure is logged and audited by the notification service and never undoes the account.
     */
    private void notifyCreated(Valid v, User user, String masked) {
        try {
            notifications.sendAdminRoleAssigned(user.getId(), user.getEmail(), user.getFirstName(),
                    "Administrator account created from " + v.slot() + " configuration", Instant.now());
        } catch (RuntimeException e) {
            log.warn("{}: the notification for {} could not be queued: {}", v.slot(), masked, e.getClass().getSimpleName());
        }
    }

    private Result handleExisting(Valid v, User existing, String masked) {
        if (existing.getRole() == Role.ADMIN) {
            log.info("{}: administrator {} already exists; password, names and status are left unchanged.", v.slot(), masked);
            return new Result(v.slot(), Outcome.ALREADY_ADMIN, masked);
        }
        if (!properties.isPromoteExisting()) {
            log.warn("{}: an account for {} already exists with role {}. It was not promoted. "
                    + "Set ADMINS_PROMOTE_EXISTING=true if that is intended.", v.slot(), masked,
                    existing.getRole() == null ? "NONE" : existing.getRole().name());
            return new Result(v.slot(), Outcome.LEFT_UNCHANGED, "existing account not promoted");
        }
        if (!existing.isVerified() || !existing.isActive() || existing.isArchived()) {
            log.warn("{}: account {} exists but is not verified and active, so it was not promoted.", v.slot(), masked);
            return new Result(v.slot(), Outcome.LEFT_UNCHANGED, "existing account is not verified and active");
        }
        boolean promoted = adminAccess.promote(existing, "SYSTEM",
                "Existing verified account promoted from " + v.slot() + " configuration", "CONFIG");
        log.info("{}: existing account {} promoted to ADMIN. Its password was not changed.", v.slot(), masked);
        return new Result(v.slot(), promoted ? Outcome.PROMOTED : Outcome.ALREADY_ADMIN, masked);
    }

    /** Pure validation and normalization; no I/O, so it can be tested without a database. */
    static Validation validate(String slotName, AdminsProperties.Slot slot, int minPasswordLength) {
        List<String> problems = new ArrayList<>();

        String email = slot.getEmail() == null ? "" : slot.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty()) {
            problems.add("email is missing (sign-in is by email)");
        } else if (email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email).matches()) {
            problems.add("email is not a valid address");
        }

        String username = slot.getUsername() == null ? "" : slot.getUsername().trim().toLowerCase(Locale.ROOT);
        if (!username.isEmpty() && !USERNAME.matcher(username).matches()) {
            problems.add("username must be 3 to 30 characters: letters, digits, dot, underscore or hyphen");
        }

        String first = clean(slot.getFirstName());
        String middle = clean(slot.getMiddleName());
        String last = clean(slot.getLastName());
        if (first.isEmpty()) {
            problems.add("first name is missing");
        }
        for (String part : new String[]{first, middle, last}) {
            if (part.length() > MAX_NAME_LENGTH) {
                problems.add("a name is longer than " + MAX_NAME_LENGTH + " characters");
                break;
            }
            if (part.chars().anyMatch(c -> c < 0x20 || c == 0x7f || c == '<' || c == '>')) {
                problems.add("a name contains control characters or angle brackets");
                break;
            }
        }

        for (String p : AdminPasswordPolicy.problems(slot.getPassword(), minPasswordLength, email, username)) {
            problems.add("password " + p);
        }

        if (!problems.isEmpty()) {
            return new Validation(null, problems);
        }
        return new Validation(new Valid(slotName, email, username, first, middle, last), List.of());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}