package com.premisave.auth.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.premisave.auth.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * All Cloudinary handling for profile pictures: manual uploads (async),
 * importing social-provider pictures (sync), and deleting replaced pictures.
 *
 * This is deliberately a separate bean from ProfileService: @Async only
 * works when the method is called through the Spring proxy, i.e. from
 * another bean. A call from inside the same class bypasses the proxy and
 * runs synchronously.
 *
 * Public IDs include the folder ("premisave/profile-photos/user_...") and no
 * separate "folder" option is sent, so the ID — and therefore the
 * precomputed delivery URL — is the same in Cloudinary's fixed-folder and
 * dynamic-folder account modes.
 */
@Slf4j
@Service
public class ProfilePictureStorage {

    private static final String FOLDER = "premisave/profile-photos";
    private static final String DELIVERY_TRANSFORMATION = "w_400,h_400,c_fill,q_auto,f_auto";

    /** A URL path segment made only of transformation components, e.g. "w_400,h_400,c_fill". */
    private static final Pattern TRANSFORMATION_SEGMENT =
            Pattern.compile("^[a-z]{1,3}_[^,/]+(,[a-z]{1,3}_[^,/]+)*$");

    /** A version segment, e.g. "v1712345678". */
    private static final Pattern VERSION_SEGMENT = Pattern.compile("^v\\d+$");

    private final Cloudinary cloudinary;
    private final MongoTemplate mongoTemplate;
    private final String cloudName;

    public ProfilePictureStorage(Cloudinary cloudinary,
                                 MongoTemplate mongoTemplate,
                                 @Value("${cloudinary.cloud-name}") String cloudName) {
        this.cloudinary = cloudinary;
        this.mongoTemplate = mongoTemplate;
        this.cloudName = cloudName;
    }

    // ─────────────────────────────────────────────────────────────
    //  URLs and IDs
    // ─────────────────────────────────────────────────────────────

    /** True when the URL points at this Cloudinary account. */
    public boolean isHostedByUs(String url) {
        return url != null && url.startsWith("https://res.cloudinary.com/" + cloudName + "/");
    }

    /** New unique public ID for a user's picture, folder included. */
    public String newPublicId(String userId) {
        return FOLDER + "/user_" + userId + "_" + System.currentTimeMillis();
    }

    /** 400x400 delivery URL for a public ID. Deterministic, so it can be computed before the upload. */
    public String deliveryUrl(String publicId) {
        return "https://res.cloudinary.com/" + cloudName + "/image/upload/"
                + DELIVERY_TRANSFORMATION + "/" + publicId;
    }

    // ─────────────────────────────────────────────────────────────
    //  Manual upload (async)
    // ─────────────────────────────────────────────────────────────

    /**
     * Uploads a user-selected picture in the background. The caller has
     * already saved deliveryUrl(publicId) on the user.
     *
     * On success the previous picture is deleted. On failure the user's URL
     * is reverted to the previous one, but only if it still points at the
     * failed upload (so a newer upload is never overwritten).
     */
    @Async
    public void uploadAsync(byte[] fileBytes, String publicId, String userId, String previousUrl) {
        String newUrl = deliveryUrl(publicId);
        try {
            cloudinary.uploader().upload(fileBytes, ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", "image",
                    "overwrite", true,
                    "timeout", 60));
            log.info("Profile picture uploaded for user {}", userId);
        } catch (Exception e) {
            log.error("Profile picture upload failed for user {}: {}", userId, e.getMessage());
            revertIfUnchanged(userId, newUrl, previousUrl);
            return;
        }

        // Only remove the old picture once the new one is safely stored
        deleteByUrl(previousUrl);
    }

    private void revertIfUnchanged(String userId, String failedUrl, String previousUrl) {
        try {
            Query query = Query.query(Criteria.where("_id").is(userId).and("profilePictureUrl").is(failedUrl));
            Update update = previousUrl == null
                    ? new Update().unset("profilePictureUrl")
                    : Update.update("profilePictureUrl", previousUrl);

            if (mongoTemplate.updateFirst(query, update, User.class).getModifiedCount() > 0) {
                log.warn("Reverted profile picture for user {} after failed upload", userId);
            }
        } catch (Exception e) {
            log.error("Could not revert profile picture for user {}: {}", userId, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Social provider picture import (sync)
    // ─────────────────────────────────────────────────────────────

    /**
     * Copies a provider-hosted picture (Google, Facebook, GitHub) into
     * Cloudinary and returns the delivery URL. Cloudinary fetches the remote
     * URL itself. Returns sourceUrl unchanged if the import fails.
     */
    public String importFromUrl(String sourceUrl, String userId) {
        try {
            String publicId = newPublicId(userId);
            Map<?, ?> result = cloudinary.uploader().upload(sourceUrl, ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", "image",
                    "overwrite", true,
                    "timeout", 30));

            Object storedId = result.get("public_id");
            log.info("Imported provider profile picture to Cloudinary for user {}", userId);
            return deliveryUrl(storedId != null ? storedId.toString() : publicId);

        } catch (Exception e) {
            log.warn("Could not import provider profile picture for user {}: {}", userId, e.getMessage());
            return sourceUrl;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Delete
    // ─────────────────────────────────────────────────────────────

    /** Deletes a picture from Cloudinary. Ignores null and URLs not hosted on this account. */
    public void deleteByUrl(String url) {
        if (!isHostedByUs(url)) {
            return;
        }

        String publicId = extractPublicId(url);
        if (publicId == null) {
            log.warn("Could not determine Cloudinary public ID from URL: {}", url);
            return;
        }

        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", "image",
                    "invalidate", true));
            log.info("Deleted old profile picture {} (result: {})", publicId, result.get("result"));
        } catch (Exception e) {
            log.warn("Failed to delete old profile picture {}: {}", publicId, e.getMessage());
        }
    }

    /**
     * Extracts the public ID from a delivery URL by skipping transformation
     * and version segments after "/upload/", and dropping any file extension.
     *
     * .../image/upload/w_400,h_400,c_fill,q_auto,f_auto/premisave/profile-photos/user_1_2
     * .../image/upload/v1712345678/premisave/profile-photos/user_1_2.jpg
     *   both → premisave/profile-photos/user_1_2
     */
    String extractPublicId(String url) {
        int uploadIndex = url.indexOf("/upload/");
        if (uploadIndex < 0) {
            return null;
        }

        String path = url.substring(uploadIndex + "/upload/".length());
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }

        String[] segments = path.split("/");
        int start = 0;

        // The last segment is always part of the public ID, never skipped
        while (start < segments.length - 1 && TRANSFORMATION_SEGMENT.matcher(segments[start]).matches()) {
            start++;
        }
        if (start < segments.length - 1 && VERSION_SEGMENT.matcher(segments[start]).matches()) {
            start++;
        }

        String publicId = String.join("/", Arrays.copyOfRange(segments, start, segments.length));

        int lastSlash = publicId.lastIndexOf('/');
        int lastDot = publicId.lastIndexOf('.');
        if (lastDot > lastSlash) {
            publicId = publicId.substring(0, lastDot);
        }

        return publicId.isEmpty() ? null : publicId;
    }
}