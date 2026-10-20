package com.premisave.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything under app.admins.* in application.yml: up to three administrator accounts that are
 * created at startup from environment variables (ADMIN1_*, ADMIN2_*, ADMIN3_*).
 *
 * Nothing here has a usable default. A slot with no values is simply unused, so a missing
 * variable can never create an account, and no password exists anywhere in the source code.
 * Values are read once at startup; changing them needs a restart.
 *
 * The password is never part of toString(), so a log line or a debugger view of this object
 * cannot leak it.
 */
@ConfigurationProperties(prefix = "app.admins")
public class AdminsProperties {

    /** Master switch. When false no administrator is created from ADMIN1 to ADMIN3. */
    private boolean enabled = true;

    /**
     * When true, a slot that fails validation stops the application from starting.
     * When false (default) that slot is skipped, the reason is logged, and the rest continue.
     */
    private boolean strict = false;

    /**
     * Allow an already existing verified, active, non-administrator account with the same email
     * to be promoted to ADMIN. Off by default so that configuration alone can never escalate
     * the privileges of an account that somebody registered earlier.
     */
    private boolean promoteExisting = false;

    /** Minimum password length accepted for a configured administrator. Never lower than 8. */
    private int minPasswordLength = 8;

    private Slot admin1 = new Slot();
    private Slot admin2 = new Slot();
    private Slot admin3 = new Slot();

    /** The three slots in order, with their names as used in log lines (ADMIN1 ...). */
    public List<NamedSlot> slots() {
        List<NamedSlot> result = new ArrayList<>(3);
        result.add(new NamedSlot("ADMIN1", admin1));
        result.add(new NamedSlot("ADMIN2", admin2));
        result.add(new NamedSlot("ADMIN3", admin3));
        return result;
    }

    public record NamedSlot(String name, Slot slot) {
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isStrict() { return strict; }
    public void setStrict(boolean strict) { this.strict = strict; }
    public boolean isPromoteExisting() { return promoteExisting; }
    public void setPromoteExisting(boolean promoteExisting) { this.promoteExisting = promoteExisting; }
    public int getMinPasswordLength() { return minPasswordLength; }
    public void setMinPasswordLength(int minPasswordLength) { this.minPasswordLength = minPasswordLength; }
    public Slot getAdmin1() { return admin1; }
    public void setAdmin1(Slot admin1) { this.admin1 = admin1; }
    public Slot getAdmin2() { return admin2; }
    public void setAdmin2(Slot admin2) { this.admin2 = admin2; }
    public Slot getAdmin3() { return admin3; }
    public void setAdmin3(Slot admin3) { this.admin3 = admin3; }

    @Override
    public String toString() {
        return "AdminsProperties{enabled=" + enabled + ", strict=" + strict + ", promoteExisting=" + promoteExisting
                + ", minPasswordLength=" + minPasswordLength + ", admin1=" + admin1 + ", admin2=" + admin2
                + ", admin3=" + admin3 + '}';
    }

    /** One administrator. All fields are empty by default. */
    public static class Slot {

        /** Sign-in address. Sign-in in this service is by email, so it is required for a slot to be used. */
        private String email = "";
        /** Public handle shown in the product. Optional; generated from the name when empty. */
        private String username = "";
        private String firstName = "";
        private String middleName = "";
        private String lastName = "";
        /** Plain text only inside this object; it is hashed with BCrypt before anything is stored. */
        private String password = "";

        /** True when nothing at all was configured, so the slot is intentionally unused. */
        public boolean isEmpty() {
            return blank(email) && blank(username) && blank(firstName) && blank(middleName)
                    && blank(lastName) && blank(password);
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getMiddleName() { return middleName; }
        public void setMiddleName(String middleName) { this.middleName = middleName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        @Override
        public String toString() {
            return "Slot{configured=" + !isEmpty() + ", password=" + (blank(password) ? "(not set)" : "****") + '}';
        }
    }
}