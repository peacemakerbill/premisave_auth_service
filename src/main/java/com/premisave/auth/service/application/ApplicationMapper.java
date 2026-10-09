package com.premisave.auth.service.application;

import com.premisave.auth.dto.application.ApplicationDtos.ApplicationResponse;
import com.premisave.auth.dto.application.ApplicationDtos.ApplicationSummary;
import com.premisave.auth.dto.application.ApplicationDtos.DocumentView;
import com.premisave.auth.dto.application.ApplicationDtos.EventView;
import com.premisave.auth.dto.application.ApplicationDtos.NoteView;
import com.premisave.auth.dto.application.ApplicationDtos.ReadinessView;
import com.premisave.auth.dto.application.ApplicationDtos.ReviewerView;
import com.premisave.auth.entity.ApplicationDocument;
import com.premisave.auth.entity.ApplicationEvent;
import com.premisave.auth.entity.HomeOwnerApplication;
import com.premisave.auth.enums.ApplicationStatus;
import com.premisave.auth.enums.DocumentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Turns stored applications into the JSON the applicant and staff see. */
@Component
public class ApplicationMapper {

    private static final String STAFF_LABEL = "Premisave review team";

    private final int reviewSlaDays;

    public ApplicationMapper(@Value("${home-owner-application.review-sla-days:3}") int reviewSlaDays) {
        this.reviewSlaDays = reviewSlaDays;
    }

    public String reviewTimeHint() {
        return "Most applications are reviewed within " + reviewSlaDays + " working days of being submitted.";
    }

    /**
     * @param staffView true for reviewers: includes internal notes, real reviewer names and the Drive folder link
     * @param basePath  base URL path used to build document download paths
     */
    public ApplicationResponse toResponse(HomeOwnerApplication a, boolean staffView, String basePath) {
        ReadinessView readiness = ApplicationReadiness.evaluate(a);
        ApplicationStatus status = a.getStatus();

        List<DocumentView> documents = new ArrayList<>();
        if (a.getDocuments() != null) {
            a.getDocuments().stream()
                    .sorted(Comparator.comparing(ApplicationDocument::getUploadedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(d -> documents.add(toDocumentView(d, a, staffView, basePath)));
        }

        List<EventView> timeline = new ArrayList<>();
        if (a.getEvents() != null) {
            a.getEvents().stream()
                    .sorted(Comparator.comparing(ApplicationEvent::getAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .forEach(e -> timeline.add(new EventView(
                            e.getType(), e.getFromStatus(), e.getToStatus(),
                            actorLabel(e, staffView), e.getMessage(), e.getAt())));
        }

        final List<NoteView> noteViews = new ArrayList<>();
        if (staffView && a.getNotes() != null) {
            a.getNotes().stream()
                    .sorted(Comparator.comparing(com.premisave.auth.entity.ReviewNote::getAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .forEach(n -> noteViews.add(new NoteView(
                            n.getId(), n.getAuthorName(), n.getAuthorRole(), n.getText(), n.getAt())));
        }
        List<NoteView> notes = staffView ? noteViews : null;

        ReviewerView reviewer = a.getAssignedReviewerId() == null ? null
                : new ReviewerView(staffView ? a.getAssignedReviewerId() : null,
                staffView ? a.getAssignedReviewerName() : STAFF_LABEL);

        return new ApplicationResponse(
                a.getId(), a.getApplicationNumber(), status, status.getLabel(), status.getDescription(),
                status.getColor(), nextStep(a, readiness), reviewTimeHint(),
                a.getLatestReviewerMessage(), a.getDecisionReason(), status.isApplicantEditable(),

                a.getApplicantId(), a.getApplicantName(), a.getApplicantEmail(),

                a.getOwnerType(), a.getCompanyName(), a.getCompanyRegistrationNumber(),

                a.getIdType(), a.getIdNumber(), a.getIdIssuingCountry(), a.getTaxId(),
                a.getDateOfBirth(), a.getNationality(),

                a.getPhoneNumber(), a.getAlternatePhoneNumber(), a.getCountry(), a.getRegion(), a.getCity(),
                a.getPhysicalAddress(), a.getPostalAddress(),

                a.getNumberOfProperties(), a.getEstimatedTotalUnits(), a.getPropertyTypes(),
                a.getPrimaryPropertyLocation(), a.getPropertyDescription(), a.getManagementPreference(),
                a.getYearsAsLandlord(),

                a.getPayoutMethod(), a.getMobileMoneyProvider(), a.getMobileMoneyNumber(), a.getPaypalEmail(),
                a.getBankName(), a.getBankAccountName(), a.getBankAccountNumber(), a.getBankSwiftCode(),
                a.getBankBranch(),

                a.getMotivation(), a.getReferralSource(),

                a.isTermsAccepted(), a.isPrivacyConsent(), a.isDeclarationAccepted(),

                documents, readiness, timeline,

                reviewer, a.getSubmissionCount(), a.getSubmittedAt(), a.getDecidedAt(),
                a.getCreatedAt(), a.getUpdatedAt(),

                notes, staffView ? a.getDriveFolderUrl() : null);
    }

    public ApplicationSummary toSummary(HomeOwnerApplication a) {
        ReadinessView readiness = ApplicationReadiness.evaluate(a);
        List<ApplicationDocument> docs = a.getDocuments() == null ? List.of() : a.getDocuments();
        int verified = (int) docs.stream().filter(d -> d.getStatus() == DocumentStatus.VERIFIED).count();
        int rejected = (int) docs.stream().filter(d -> d.getStatus() == DocumentStatus.REJECTED).count();

        long waitingHours = 0;
        if (a.getSubmittedAt() != null && !a.getStatus().isFinalState()) {
            LocalDateTime since = a.getLastSubmittedAt() != null ? a.getLastSubmittedAt() : a.getSubmittedAt();
            waitingHours = Math.max(0, Duration.between(since, LocalDateTime.now()).toHours());
        }

        ReviewerView reviewer = a.getAssignedReviewerId() == null ? null
                : new ReviewerView(a.getAssignedReviewerId(), a.getAssignedReviewerName());

        return new ApplicationSummary(
                a.getId(), a.getApplicationNumber(), a.getStatus(), a.getStatus().getLabel(),
                a.getStatus().getColor(), a.getApplicantName(), a.getApplicantEmail(), a.getPhoneNumber(),
                a.getOwnerType(), a.getCountry(), a.getRegion(), a.getNumberOfProperties(), docs.size(), verified, rejected,
                readiness.percent(), reviewer, a.getSubmissionCount(), a.getSubmittedAt(), waitingHours,
                a.getUpdatedAt());
    }

    private DocumentView toDocumentView(ApplicationDocument d, HomeOwnerApplication a, boolean staffView, String basePath) {
        return new DocumentView(
                d.getId(), d.getType(), d.getType() == null ? null : d.getType().getLabel(),
                d.getOriginalFileName(), d.getMimeType(), d.getSizeBytes(), d.getDocumentNumber(),
                d.getExpiryDate(), d.getStatus(), d.getStatus() == null ? null : d.getStatus().getLabel(),
                d.getReviewReason(), staffView ? d.getReviewedByName() : (d.getReviewedAt() == null ? null : STAFF_LABEL),
                d.getReviewedAt(), d.getUploadedAt(),
                basePath + "/" + a.getId() + "/documents/" + d.getId() + "/download");
    }

    private String actorLabel(ApplicationEvent e, boolean staffView) {
        if (e.isByApplicant()) {
            return staffView ? e.getActorName() : "You";
        }
        if (staffView) {
            return e.getActorName() == null ? "System" : e.getActorName();
        }
        return STAFF_LABEL;
    }

    /** One plain sentence telling the applicant what to do now. */
    String nextStep(HomeOwnerApplication a, ReadinessView readiness) {
        return switch (a.getStatus()) {
            case DRAFT -> readiness.canSubmit()
                    ? "Everything is in place. Review your details and press Submit."
                    : "Complete the remaining items to submit: " + readiness.issues().get(0).message() + ".";
            case SUBMITTED -> "We have your application. A reviewer will pick it up soon, and you will get an email at every step.";
            case PROCESSING -> "A reviewer is checking your details and documents. Nothing is needed from you right now.";
            case INFO_REQUESTED -> "Read the reviewer's message, fix or replace what was flagged, then resubmit.";
            case VERIFIED -> "Your details and documents are verified. Final approval is the last step.";
            case ACCEPTED -> "You are now a Home Owner. Sign out and back in if you do not see the new features yet.";
            case REJECTED -> "This application was not approved. You can start a new application when you are ready.";
            case WITHDRAWN -> "You withdrew this application. You can start a new one at any time.";
        };
    }
}