package com.premisave.auth.service;

import com.premisave.auth.dto.AuthResponse;
import com.premisave.auth.dto.OAuthRequest;
import com.premisave.auth.dto.OAuthUserInfo;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.Language;
import com.premisave.auth.enums.Role;
import com.premisave.auth.repository.UserRepository;
import com.premisave.auth.security.JwtService;
import com.premisave.auth.service.oauth.OAuthProviderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Social sign-up and sign-in (Google, Facebook, GitHub).
 *
 * Resolution order for an incoming social login:
 *   1. a user already linked to this provider account (provider id)
 *   2. an existing user with the same, provider-verified email — linked on the spot
 *   3. otherwise a new CLIENT account is created, already verified
 *
 * The provider's profile picture is copied to Cloudinary whenever the user
 * has no picture hosted by us yet, so it is captured on sign-up and repaired
 * on later sign-ins if an earlier import failed.
 */
@Slf4j
@Service
public class OAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ProfilePictureStorage pictureStorage;
    private final Map<String, OAuthProviderClient> clients;

    public OAuthService(UserRepository userRepository,
                        JwtService jwtService,
                        PasswordEncoder passwordEncoder,
                        ProfilePictureStorage pictureStorage,
                        List<OAuthProviderClient> providerClients) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.pictureStorage = pictureStorage;
        this.clients = providerClients.stream()
                .collect(Collectors.toUnmodifiableMap(OAuthProviderClient::provider, Function.identity()));
    }

    // ─────────────────────────────────────────────────────────────
    //  Entry point
    // ─────────────────────────────────────────────────────────────

    public AuthResponse handleOAuth(OAuthRequest request) {
        String provider = request.getProvider().trim().toLowerCase(Locale.ROOT);

        OAuthProviderClient client = clients.get(provider);
        if (client == null) {
            throw new RuntimeException("Unsupported OAuth provider: " + provider
                    + ". Supported providers: google, facebook, github");
        }

        OAuthUserInfo info = client.fetchUser(request);
        if (isBlank(info.getProviderId())) {
            throw new RuntimeException("The " + provider + " account id could not be determined");
        }
        if (isBlank(info.getEmail())) {
            throw new RuntimeException("The " + provider + " account did not provide an email address");
        }

        User user = findOrCreateUser(info);
        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);

        log.info("OAuth sign-in via {} for user {}", provider, user.getId());
        return new AuthResponse(jwtService.generateToken(user), user.getRole().name());
    }

    // ─────────────────────────────────────────────────────────────
    //  Account resolution
    // ─────────────────────────────────────────────────────────────

    private User findOrCreateUser(OAuthUserInfo info) {
        String provider = info.getProvider();
        String providerId = info.getProviderId();
        String rawEmail = info.getEmail().trim();
        String email = rawEmail.toLowerCase(Locale.ROOT);

        // 1. Already linked to this provider account
        Optional<User> linked = findByProviderId(provider, providerId);
        if (linked.isPresent()) {
            User user = linked.get();
            ensureCanSignIn(user);
            syncProfile(user, info);
            return user;
        }

        // 2. Existing account with the same verified email: link it
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isEmpty() && !email.equals(rawEmail)) {
            byEmail = userRepository.findByEmail(rawEmail);
        }
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            ensureCanSignIn(user);

            String existingId = getProviderId(user, provider);
            if (existingId != null && !existingId.equals(providerId)) {
                throw new RuntimeException("This email is already linked to a different "
                        + displayName(provider) + " account");
            }
            setProviderId(user, provider, providerId);

            // The provider has confirmed ownership of this email
            if (!user.isVerified()) {
                user.setVerified(true);
            }

            syncProfile(user, info);
            log.info("Linked {} account to existing user {}", provider, user.getId());
            return user;
        }

        // 3. New user
        return createUser(info, email);
    }

    private User createUser(OAuthUserInfo info, String email) {
        NameParts names = resolveNames(info, email);

        User user = new User();
        user.setEmail(email);
        user.setFirstName(names.first());
        user.setLastName(names.last());
        user.setUsername(resolveUniqueUsername(baseUsername(info, email)));
        user.setRole(Role.CLIENT);
        user.setActive(true);
        user.setVerified(true);   // The provider has verified the email
        user.setArchived(false);
        user.setLanguage(Language.ENGLISH);

        // Social accounts have no usable password; store an encoded random one
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        setProviderId(user, info.getProvider(), info.getProviderId());

        // Save first so the picture can be stored under the user's id
        user = userRepository.save(user);

        if (!isBlank(info.getProfilePictureUrl())) {
            user.setProfilePictureUrl(pictureStorage.importFromUrl(info.getProfilePictureUrl(), user.getId()));
        }

        log.info("New user {} created via {}", user.getId(), info.getProvider());
        return user;
    }

    /** Fills gaps only — never overwrites names or a picture the user already has on Cloudinary. */
    private void syncProfile(User user, OAuthUserInfo info) {
        NameParts names = resolveNames(info, user.getEmail());
        if (isBlank(user.getFirstName())) {
            user.setFirstName(names.first());
        }
        if (isBlank(user.getLastName()) && !isBlank(names.last())) {
            user.setLastName(names.last());
        }

        if (!isBlank(info.getProfilePictureUrl()) && !pictureStorage.isHostedByUs(user.getProfilePictureUrl())) {
            user.setProfilePictureUrl(pictureStorage.importFromUrl(info.getProfilePictureUrl(), user.getId()));
        }
    }

    private void ensureCanSignIn(User user) {
        if (user.isArchived()) {
            throw new RuntimeException("This account has been archived. Please contact support.");
        }
        if (!user.isActive()) {
            throw new RuntimeException("Account is deactivated. Please contact support.");
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Provider id fields on User
    // ─────────────────────────────────────────────────────────────

    private Optional<User> findByProviderId(String provider, String providerId) {
        return switch (provider) {
            case "google" -> userRepository.findByGoogleId(providerId);
            case "facebook" -> userRepository.findByFacebookId(providerId);
            case "github" -> userRepository.findByGithubId(providerId);
            default -> Optional.empty();
        };
    }

    private String getProviderId(User user, String provider) {
        return switch (provider) {
            case "google" -> user.getGoogleId();
            case "facebook" -> user.getFacebookId();
            case "github" -> user.getGithubId();
            default -> null;
        };
    }

    private void setProviderId(User user, String provider, String providerId) {
        switch (provider) {
            case "google" -> user.setGoogleId(providerId);
            case "facebook" -> user.setFacebookId(providerId);
            case "github" -> user.setGithubId(providerId);
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        }
    }

    private String displayName(String provider) {
        return switch (provider) {
            case "google" -> "Google";
            case "facebook" -> "Facebook";
            case "github" -> "GitHub";
            default -> provider;
        };
    }

    // ─────────────────────────────────────────────────────────────
    //  Names and usernames
    // ─────────────────────────────────────────────────────────────

    private record NameParts(String first, String last) {
    }

    /** Uses first/last name when given, otherwise splits the full name, otherwise the email prefix. */
    private NameParts resolveNames(OAuthUserInfo info, String email) {
        String first = trimToNull(info.getFirstName());
        String last = trimToNull(info.getLastName());

        if (first == null && last == null) {
            String full = trimToNull(info.getFullName());
            if (full != null) {
                int split = full.lastIndexOf(' ');
                if (split > 0) {
                    first = full.substring(0, split).trim();
                    last = full.substring(split + 1).trim();
                } else {
                    first = full;
                }
            }
        }

        if (first == null) {
            first = email.split("@")[0];
        }
        return new NameParts(first, last);
    }

    private String baseUsername(OAuthUserInfo info, String email) {
        String base = !isBlank(info.getUsernameHint()) ? info.getUsernameHint() : email.split("@")[0];

        // Same character set ProfileUpdateRequest allows: letters, digits, dot, underscore, hyphen
        base = base.replaceAll("[^a-zA-Z0-9_.-]", "");
        if (base.length() < 3) {
            base = base + "user";
        }
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        return base;
    }

    /** Appends a numeric suffix if taken: "johndoe", then "johndoe2", then "johndoe3". */
    private String resolveUniqueUsername(String base) {
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        int suffix = 2;
        while (userRepository.existsByUsername(base + suffix)) {
            suffix++;
        }
        return base + suffix;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}