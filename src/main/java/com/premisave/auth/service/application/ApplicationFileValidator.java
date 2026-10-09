package com.premisave.auth.service.application;

import com.premisave.auth.exception.ApiException;

import java.util.List;
import java.util.Locale;

/**
 * Checks an uploaded file by its content, not by what the client claims.
 * Only PDF, JPEG, PNG and WEBP are accepted.
 */
final class ApplicationFileValidator {

    record Detected(String mimeType, String extension) {
    }

    static final List<String> ALLOWED_MIME_TYPES =
            List.of("application/pdf", "image/jpeg", "image/png", "image/webp");

    static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "jpg", "jpeg", "png", "webp");

    private ApplicationFileValidator() {
    }

    static Detected detect(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if (data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F' && data[4] == '-') {
            return new Detected("application/pdf", "pdf");
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return new Detected("image/jpeg", "jpg");
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return new Detected("image/png", "png");
        }
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return new Detected("image/webp", "webp");
        }
        return null;
    }

    /** Throws a friendly error if the file is empty, too big or not an accepted type. */
    static Detected validate(byte[] data, long maxBytes, String maxLabel) {
        if (data == null || data.length == 0) {
            throw ApiException.badRequest("The file is empty. Please choose a file to upload.");
        }
        if (data.length > maxBytes) {
            throw ApiException.badRequest("That file is larger than " + maxLabel + ". Please upload a smaller file.");
        }
        Detected detected = detect(data);
        if (detected == null) {
            throw ApiException.badRequest("Only PDF, JPG, PNG or WEBP files are accepted.");
        }
        return detected;
    }

    static String cleanFileName(String original) {
        if (original == null || original.isBlank()) {
            return "document";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        name = name.replaceAll("[^A-Za-z0-9._ ()\\-]", "_");
        if (name.length() > 120) {
            name = name.substring(name.length() - 120);
        }
        return name.isEmpty() ? "document" : name;
    }

    static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}