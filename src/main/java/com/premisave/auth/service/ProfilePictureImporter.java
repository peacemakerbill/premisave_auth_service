package com.premisave.auth.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Copies a provider-hosted profile picture (Google, Facebook, GitHub) into
 * Cloudinary so the stored URL is permanent. Facebook picture URLs in
 * particular are signed and expire, so storing them directly breaks later.
 *
 * Cloudinary fetches the remote URL itself; no bytes pass through this service.
 * On failure the original provider URL is returned, and the import is retried
 * on the user's next social sign-in.
 */
@Slf4j
@Service
public class ProfilePictureImporter {

    private static final String FOLDER = "premisave/profile-photos";
    private static final String DELIVERY_TRANSFORMATION = "w_400,h_400,c_fill,q_auto,f_auto";

    private final Cloudinary cloudinary;
    private final String cloudName;

    public ProfilePictureImporter(Cloudinary cloudinary,
                                  @Value("${cloudinary.cloud-name}") String cloudName) {
        this.cloudinary = cloudinary;
        this.cloudName = cloudName;
    }

    /** True when the URL already points at this Cloudinary account. */
    public boolean isHostedByUs(String url) {
        return url != null && url.startsWith("https://res.cloudinary.com/" + cloudName + "/");
    }

    /**
     * Uploads the image at sourceUrl to Cloudinary and returns the delivery URL
     * (same 400x400 format ProfileService uses). Returns sourceUrl if the upload fails.
     */
    public String importFromUrl(String sourceUrl, String userId) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(sourceUrl, ObjectUtils.asMap(
                    "public_id", "user_" + userId + "_" + System.currentTimeMillis(),
                    "folder", FOLDER,
                    "resource_type", "image",
                    "overwrite", true,
                    "timeout", 30));

            Object publicId = result.get("public_id");
            if (publicId == null) {
                throw new IllegalStateException("Cloudinary returned no public_id");
            }

            String hostedUrl = String.format("https://res.cloudinary.com/%s/image/upload/%s/%s",
                    cloudName, DELIVERY_TRANSFORMATION, publicId);
            log.info("Imported provider profile picture to Cloudinary for user {}", userId);
            return hostedUrl;

        } catch (Exception e) {
            log.warn("Could not import provider profile picture for user {}: {}", userId, e.getMessage());
            return sourceUrl;
        }
    }
}