package com.premisave.auth.entity;

import com.premisave.auth.enums.ApplicationStatus;
import com.premisave.auth.enums.ManagementPreference;
import com.premisave.auth.enums.OwnerType;
import com.premisave.auth.enums.PayoutMethod;
import com.premisave.auth.enums.PropertyType;
import com.premisave.auth.enums.ReferralSource;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A request by a CLIENT to become a HOME_OWNER.
 *
 * Name, email and the other applicant details are snapshots taken when the
 * application is created, so the review record stays stable even if the
 * profile changes later. Files are stored in Google Drive; only their ids are kept here.
 */
@Data
@Document(collection = "home_owner_applications")
public class HomeOwnerApplication {

    @Id
    private String id;

    /** Human friendly reference, for example HOA-2026-000123. */
    @Indexed(unique = true)
    private String applicationNumber;

    @Indexed
    private String applicantId;
    private String applicantEmail;
    private String applicantName;

    @Indexed
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    private OwnerType ownerType = OwnerType.INDIVIDUAL;

    // Business (company or organisation)
    private String companyName;
    private String companyRegistrationNumber;

    // Identity and tax
    private String nationalIdNumber;
    private String passportNumber;
    private String drivingLicenceNumber;
    private String kraPin;
    private LocalDate dateOfBirth;
    private String nationality;

    // Contact and address
    private String phoneNumber;
    private String alternatePhoneNumber;
    private String country = "Kenya";
    private String county;
    private String town;
    private String physicalAddress;
    private String postalAddress;

    // Portfolio
    private Integer numberOfProperties;
    private Integer estimatedTotalUnits;
    private List<PropertyType> propertyTypes = new ArrayList<>();
    private String primaryPropertyLocation;
    private String propertyDescription;
    private ManagementPreference managementPreference;
    private Integer yearsAsLandlord;

    // Payout
    private PayoutMethod payoutMethod;
    private String mpesaNumber;
    private String bankName;
    private String bankAccountName;
    private String bankAccountNumber;
    private String bankBranch;

    // About the applicant
    private String motivation;
    private ReferralSource referralSource;

    // Consent
    private boolean termsAccepted;
    private boolean privacyConsent;
    private boolean declarationAccepted;
    private LocalDateTime consentedAt;

    // Documents, timeline and internal notes
    private List<ApplicationDocument> documents = new ArrayList<>();
    private List<ApplicationEvent> events = new ArrayList<>();
    private List<ReviewNote> notes = new ArrayList<>();

    // Google Drive
    private String driveFolderId;
    private String driveFolderUrl;

    // Review
    private String assignedReviewerId;
    private String assignedReviewerName;
    private String latestReviewerMessage;
    private String decisionReason;
    private int submissionCount;
    private LocalDateTime submittedAt;
    private LocalDateTime lastSubmittedAt;
    private LocalDateTime reviewStartedAt;
    private LocalDateTime decidedAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}