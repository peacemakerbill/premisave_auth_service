package com.premisave.auth.dto.application;

import com.premisave.auth.enums.ApplicationEventType;
import com.premisave.auth.enums.ApplicationStatus;
import com.premisave.auth.enums.DocumentStatus;
import com.premisave.auth.enums.DocumentType;
import com.premisave.auth.enums.ManagementPreference;
import com.premisave.auth.enums.OwnerType;
import com.premisave.auth.enums.PayoutMethod;
import com.premisave.auth.enums.PropertyType;
import com.premisave.auth.enums.ReferralSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Response shapes for the Home Owner application endpoints. */
public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    /** A dropdown or checkbox option. */
    public record Option(String value, String label, String description) {
    }

    public record DocumentView(
            String id,
            DocumentType type,
            String typeLabel,
            String fileName,
            String mimeType,
            long sizeBytes,
            String documentNumber,
            LocalDate expiryDate,
            DocumentStatus status,
            String statusLabel,
            String reviewReason,
            String reviewedBy,
            LocalDateTime reviewedAt,
            LocalDateTime uploadedAt,
            String downloadPath) {
    }

    /** One line of the checklist the applicant works through. */
    public record RequirementView(
            String key,
            String label,
            String description,
            boolean required,
            List<List<DocumentType>> acceptedCombinations,
            List<DocumentType> stillNeeded,
            boolean uploaded,
            boolean verified,
            boolean needsAttention) {
    }

    public record FieldIssue(String field, String message) {
    }

    public record ReadinessView(
            int percent,
            boolean canSubmit,
            boolean allRequiredVerified,
            List<FieldIssue> issues,
            List<RequirementView> requirements) {
    }

    public record EventView(
            ApplicationEventType type,
            ApplicationStatus fromStatus,
            ApplicationStatus toStatus,
            String actor,
            String message,
            LocalDateTime at) {
    }

    public record NoteView(String id, String author, String authorRole, String text, LocalDateTime at) {
    }

    public record ReviewerView(String id, String name) {
    }

    /** Full application. Internal notes and the Drive links are filled in for staff only. */
    public record ApplicationResponse(
            String id,
            String applicationNumber,
            ApplicationStatus status,
            String statusLabel,
            String statusDescription,
            String statusColor,
            String nextStep,
            String reviewTimeHint,
            String latestMessage,
            String decisionReason,
            boolean editable,

            String applicantId,
            String applicantName,
            String applicantEmail,

            OwnerType ownerType,
            String companyName,
            String companyRegistrationNumber,

            String nationalIdNumber,
            String passportNumber,
            String drivingLicenceNumber,
            String kraPin,
            LocalDate dateOfBirth,
            String nationality,

            String phoneNumber,
            String alternatePhoneNumber,
            String country,
            String county,
            String town,
            String physicalAddress,
            String postalAddress,

            Integer numberOfProperties,
            Integer estimatedTotalUnits,
            List<PropertyType> propertyTypes,
            String primaryPropertyLocation,
            String propertyDescription,
            ManagementPreference managementPreference,
            Integer yearsAsLandlord,

            PayoutMethod payoutMethod,
            String mpesaNumber,
            String bankName,
            String bankAccountName,
            String bankAccountNumber,
            String bankBranch,

            String motivation,
            ReferralSource referralSource,

            boolean termsAccepted,
            boolean privacyConsent,
            boolean declarationAccepted,

            List<DocumentView> documents,
            ReadinessView readiness,
            List<EventView> timeline,

            ReviewerView reviewer,
            int submissionCount,
            LocalDateTime submittedAt,
            LocalDateTime decidedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,

            // Staff only (null for applicants)
            List<NoteView> internalNotes,
            String driveFolderUrl) {
    }

    /** A row in the staff queue. */
    public record ApplicationSummary(
            String id,
            String applicationNumber,
            ApplicationStatus status,
            String statusLabel,
            String statusColor,
            String applicantName,
            String applicantEmail,
            String phoneNumber,
            OwnerType ownerType,
            String county,
            Integer numberOfProperties,
            int documentCount,
            int verifiedDocumentCount,
            int rejectedDocumentCount,
            int completionPercent,
            ReviewerView reviewer,
            int submissionCount,
            LocalDateTime submittedAt,
            long waitingHours,
            LocalDateTime updatedAt) {
    }

    /** A staff member a reviewer can assign an application to. */
    public record StaffMember(String id, String name, String email, String role) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record StatsResponse(
            long total,
            Map<ApplicationStatus, Long> byStatus,
            long awaitingReview,
            long unassigned,
            long assignedToMe,
            long needsInfoFromApplicant,
            long readyToPromote,
            Double averageDecisionHours,
            LocalDateTime oldestWaitingSince,
            String oldestWaitingApplicationId) {
    }

    public record Eligibility(boolean canApply, String reason, String openApplicationId, ApplicationStatus openApplicationStatus) {
    }

    public record Limits(long maxFileSizeBytes, String maxFileSizeLabel, List<String> allowedMimeTypes,
                         List<String> allowedExtensions, int maxDocuments) {
    }

    public record DocumentTypeOption(DocumentType value, String label, String description, String category,
                                     boolean allowsMultiple, int maxFiles) {
    }

    public record RequirementOption(String key, String label, String description, boolean required,
                                    List<List<DocumentType>> acceptedCombinations) {
    }

    public record MetaResponse(
            List<Option> ownerTypes,
            List<Option> propertyTypes,
            List<Option> managementPreferences,
            List<Option> payoutMethods,
            List<Option> referralSources,
            List<Option> statuses,
            List<DocumentTypeOption> documentTypes,
            Map<OwnerType, List<RequirementOption>> requirementsByOwnerType,
            List<String> counties,
            Limits limits,
            String reviewTimeHint) {
    }

    public record PromoteResponse(
            String applicationId,
            String applicationNumber,
            String userId,
            String userEmail,
            String previousRole,
            String newRole,
            ApplicationStatus status,
            String message) {
    }
}