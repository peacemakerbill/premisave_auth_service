package com.premisave.auth.enums;

/**
 * Lifecycle of a Home Owner application.
 *
 *   DRAFT           the applicant is still filling it in
 *   SUBMITTED       waiting for a reviewer
 *   PROCESSING      a reviewer is checking the details and documents
 *   INFO_REQUESTED  the applicant must fix or add something, then resubmit
 *   VERIFIED        details and documents are confirmed; an admin can now promote the user
 *   ACCEPTED        the user has been promoted to HOME_OWNER (final)
 *   REJECTED        not approved (final, the applicant may start a new application)
 *   WITHDRAWN       cancelled by the applicant (final)
 */
public enum ApplicationStatus {

    DRAFT("Draft",
            "You have started an application but have not submitted it yet.",
            "#6b7280", false, true),
    SUBMITTED("Submitted",
            "We have received your application and it is waiting for a reviewer.",
            "#2563eb", false, false),
    PROCESSING("In review",
            "A reviewer is checking your details and documents.",
            "#d97706", false, false),
    INFO_REQUESTED("Action needed",
            "The reviewer needs something from you before the review can continue.",
            "#dc2626", false, true),
    VERIFIED("Verified",
            "Your details and documents are verified. Final approval is the last step.",
            "#0d9488", false, false),
    ACCEPTED("Accepted",
            "Your application was approved and your account is now a Home Owner account.",
            "#2a8f5e", true, false),
    REJECTED("Not approved",
            "Your application was not approved.",
            "#b91c1c", true, false),
    WITHDRAWN("Withdrawn",
            "This application was withdrawn.",
            "#6b7280", true, false);

    private final String label;
    private final String description;
    private final String color;
    private final boolean finalState;
    private final boolean applicantEditable;

    ApplicationStatus(String label, String description, String color, boolean finalState, boolean applicantEditable) {
        this.label = label;
        this.description = description;
        this.color = color;
        this.finalState = finalState;
        this.applicantEditable = applicantEditable;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public String getColor() { return color; }

    /** No further progress is expected from this state. */
    public boolean isFinalState() { return finalState; }

    /** The applicant may change details and documents in this state. */
    public boolean isApplicantEditable() { return applicantEditable; }

    /** Counts toward the one-open-application-per-user rule. */
    public boolean isOpen() { return !finalState; }
}