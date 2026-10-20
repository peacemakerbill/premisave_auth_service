package com.premisave.auth.service.security;

import com.premisave.auth.config.SecurityProperties;
import com.premisave.auth.entity.SignInRecord;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.Role;
import com.premisave.auth.enums.SecurityEventType;
import com.premisave.auth.repository.SecurityEventRepository;
import com.premisave.auth.repository.SignInRecordRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scores a successful sign-in from independent signals. No single weak signal (a new phone,
 * a VPN) reaches the alert threshold by itself, and nothing here blocks the sign-in: the
 * response to a high score is an alert to the owner, who can end the session in one click.
 *
 * Weights: new device 20 (35 for an administrator), country change inside the window 40,
 * many sign-ins in a short time 25, many different addresses 30, earlier failed attempts
 * on the account 35, a replayed refresh token in the last day 40. The default threshold is 50.
 */
@Service
public class SuspiciousActivityService {

    static final int NEW_DEVICE = 20;
    static final int NEW_DEVICE_ADMIN = 35;
    static final int COUNTRY_CHANGE = 40;
    static final int HIGH_FREQUENCY = 25;
    static final int MANY_ADDRESSES = 30;
    static final int RECENT_FAILURES = 35;
    static final int TOKEN_REUSE = 40;

    private static final int FAILURES_NEEDED = 3;
    private static final Duration FAILURE_LOOKBACK = Duration.ofMinutes(30);

    private final SignInRecordRepository signIns;
    private final SecurityEventRepository events;
    private final SecurityProperties properties;
    private final Clock clock;

    public SuspiciousActivityService(SignInRecordRepository signIns, SecurityEventRepository events,
                                     SecurityProperties properties, Clock clock) {
        this.signIns = signIns;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /** Call before the current sign-in is stored, so it is not counted against itself. */
    public RiskAssessment assess(User user, ClientContext ctx, GeoLocation geo, boolean newDevice, boolean firstDevice) {
        Instant now = Instant.now(clock);
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (newDevice && !firstDevice) {
            boolean admin = user.getRole() == Role.ADMIN;
            score += admin ? NEW_DEVICE_ADMIN : NEW_DEVICE;
            reasons.add(admin ? "First sign-in from this device on an administrator account" : "First sign-in from this device");
        }

        Duration longest = Duration.ofHours(Math.max(1, properties.getLocationChangeWindowHours()));
        Duration frequencyWindow = Duration.ofMinutes(Math.max(1, properties.getFrequencyWindowMinutes()));
        Duration addressWindow = Duration.ofMinutes(Math.max(1, properties.getDistinctIpWindowMinutes()));
        Duration lookback = longest;
        if (frequencyWindow.compareTo(lookback) > 0) {
            lookback = frequencyWindow;
        }
        if (addressWindow.compareTo(lookback) > 0) {
            lookback = addressWindow;
        }
        List<SignInRecord> recent = signIns.findByUserIdAndAtAfterOrderByAtDesc(user.getId(), now.minus(lookback));

        if (geo.country() != null) {
            Instant locationCutoff = now.minus(longest);
            boolean changed = recent.stream()
                    .filter(r -> r.getAt() != null && r.getAt().isAfter(locationCutoff))
                    .map(SignInRecord::getCountry)
                    .filter(c -> c != null && !c.isBlank())
                    .findFirst()
                    .map(previous -> !previous.equalsIgnoreCase(geo.country()))
                    .orElse(false);
            if (changed) {
                score += COUNTRY_CHANGE;
                reasons.add("Approximate country differs from a sign-in within the last "
                        + properties.getLocationChangeWindowHours() + " hours");
            }
        }

        Instant frequencyCutoff = now.minus(frequencyWindow);
        long burst = recent.stream().filter(r -> r.getAt() != null && r.getAt().isAfter(frequencyCutoff)).count();
        if (burst + 1 >= properties.getFrequencyThreshold()) {
            score += HIGH_FREQUENCY;
            reasons.add("Unusually many sign-ins in a short time");
        }

        Instant addressCutoff = now.minus(addressWindow);
        Set<String> addresses = new HashSet<>();
        recent.stream()
                .filter(r -> r.getAt() != null && r.getAt().isAfter(addressCutoff) && r.getIpAddress() != null)
                .forEach(r -> addresses.add(r.getIpAddress()));
        if (ctx.ip() != null) {
            addresses.add(ctx.ip());
        }
        if (addresses.size() >= properties.getDistinctIpThreshold()) {
            score += MANY_ADDRESSES;
            reasons.add("Sign-ins from several different network addresses");
        }

        long failures = events.countByUserIdAndTypeAndCreatedAtAfter(user.getId(), SecurityEventType.SIGN_IN_FAILED,
                now.minus(FAILURE_LOOKBACK));
        if (failures >= FAILURES_NEEDED) {
            score += RECENT_FAILURES;
            reasons.add("Several failed password attempts just before this sign-in");
        }

        long reuse = events.countByUserIdAndTypeAndCreatedAtAfter(user.getId(), SecurityEventType.REFRESH_TOKEN_REUSE,
                now.minus(Duration.ofDays(1)));
        if (reuse > 0) {
            score += TOKEN_REUSE;
            reasons.add("A previously revoked session token was used in the last day");
        }

        return new RiskAssessment(score, List.copyOf(reasons), score >= properties.getRiskAlertThreshold());
    }
}
