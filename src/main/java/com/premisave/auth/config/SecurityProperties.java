package com.premisave.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything under app.security.* in application.yml.
 *
 * Every property has a safe default, so a missing or empty environment variable
 * can never stop the application from starting. Values are read once at startup:
 * changing an environment variable needs a restart of the service.
 */
@Data
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    // Administrator provisioning

    /** Comma-separated emails that are provisioned as ADMIN at startup. */
    private String initialAdminEmails = "";

    /** Comma-separated emails that become ADMIN once the account is verified. */
    private String autoAdminEmails = "";

    /** Hours an administrator invitation link stays valid. */
    private int invitationTtlHours = 72;

    // Notifications

    private boolean loginEmailNotifications = true;
    private boolean newDeviceAlerts = true;
    private boolean suspiciousLoginAlerts = true;

    /** Hours before a recognised but untrusted device triggers another alert. 0 alerts on every sign-in. */
    private int deviceAlertCooldownHours = 24;

    /** Alert even for the very first device of a brand new account. */
    private boolean alertFirstDevice = false;

    /** Zone used to print times in emails, for example UTC or Africa/Nairobi. */
    private String displayZone = "UTC";

    /** Path of the account security page on the frontend. */
    private String securityPagePath = "/account/security";

    // Devices and sessions

    private boolean deviceTrustEnabled = true;
    private boolean sessionRevocationEnabled = true;

    private String deviceCookieName = "ps_did";
    private boolean deviceCookieSecure = true;
    private String deviceCookieSameSite = "Lax";
    private int deviceCookieMaxAgeDays = 365;
    private int maxDevicesPerUser = 50;

    /** Days a session (and its refresh token) lives without being revoked. */
    private int sessionTtlDays = 30;

    /** Minutes between lastSeen updates of one session. */
    private int sessionTouchMinutes = 5;

    /** Minutes after sign-in during which a social-login session counts as freshly authenticated. */
    private int reauthWindowMinutes = 10;

    /** Still accept an unexpired access token at POST /auth/refresh (older clients). */
    private boolean legacyRefreshEnabled = true;

    /**
     * When true, POST /auth/signup answers the same way whether or not the address already has an account:
     * a neutral message, no tokens, and the next step arrives by email. An existing owner gets a notice
     * instead of an error. Off by default because it changes the signup response clients receive.
     */
    private boolean hideExistingAccounts = false;

    // Network

    /** Comma-separated IPs or CIDR ranges of reverse proxies whose X-Forwarded-For is trusted. */
    private String trustedProxies = "";

    private boolean ipGeolocationEnabled = false;

    /** URL template; {ip} is replaced. The default is a free service, see the README for privacy notes. */
    private String ipGeolocationUrl = "https://ipwho.is/{ip}";
    private int ipGeolocationTimeoutMs = 2000;

    // Retention

    private int auditRetentionDays = 180;
    private int deviceRetentionDays = 365;

    // Brute force protection

    private int loginMaxFailures = 5;
    private int loginFailureWindowMinutes = 15;
    private int loginLockoutMinutes = 15;
    private int loginMaxLockoutMinutes = 240;
    private int ipMaxFailures = 30;
    private int ipMaxDistinctAccounts = 10;
    private int ipWindowMinutes = 15;

    // Suspicious sign-in detection

    private int riskAlertThreshold = 50;
    private int locationChangeWindowHours = 12;
    private int frequencyThreshold = 8;
    private int frequencyWindowMinutes = 10;
    private int distinctIpThreshold = 4;
    private int distinctIpWindowMinutes = 60;

    // Email action links

    private int actionTokenTtlMinutes = 1440;
}
