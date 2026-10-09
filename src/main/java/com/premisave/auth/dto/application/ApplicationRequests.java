package com.premisave.auth.dto.application;

import com.premisave.auth.enums.ApplicationStatus;
import com.premisave.auth.enums.DocumentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Small request bodies used by the review and applicant action endpoints. */
public final class ApplicationRequests {

    private ApplicationRequests() {
    }

    @Data
    public static class StatusChange {
        @NotNull(message = "Status is required")
        private ApplicationStatus status;

        /** Shown to the applicant (and emailed). Required for INFO_REQUESTED and REJECTED. */
        @Size(max = 2000, message = "Message is too long")
        private String message;

        /** Optional remark kept for staff only. */
        @Size(max = 2000, message = "Internal note is too long")
        private String internalNote;
    }

    @Data
    public static class DocumentReview {
        @NotNull(message = "Status is required (VERIFIED or REJECTED)")
        private DocumentStatus status;

        /** Required when rejecting: tell the applicant what is wrong with the file. */
        @Size(max = 1000, message = "Reason is too long")
        private String reason;

        /** When rejecting, also move the application to INFO_REQUESTED so the applicant is asked to re-upload. */
        private boolean requestReupload;
    }

    @Data
    public static class Note {
        @NotBlank(message = "Note cannot be empty")
        @Size(max = 2000, message = "Note is too long")
        private String text;
    }

    @Data
    public static class Assign {
        @NotBlank(message = "Reviewer id is required")
        private String reviewerId;
    }

    @Data
    public static class Promote {
        /** Optional welcome message added to the email the applicant receives. */
        @Size(max = 1000, message = "Message is too long")
        private String message;

        @Size(max = 2000, message = "Internal note is too long")
        private String internalNote;
    }

    @Data
    public static class Withdraw {
        @Size(max = 1000, message = "Reason is too long")
        private String reason;
    }
}