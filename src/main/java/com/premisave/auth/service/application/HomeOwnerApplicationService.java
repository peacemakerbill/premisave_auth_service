package com.premisave.auth.service.application;

import com.premisave.auth.dto.application.ApplicationDtos.ApplicationResponse;
import com.premisave.auth.dto.application.ApplicationDtos.ApplicationSummary;
import com.premisave.auth.dto.application.ApplicationDtos.Eligibility;
import com.premisave.auth.dto.application.ApplicationDtos.ReadinessView;
import com.premisave.auth.dto.application.ApplicationRequest;
import com.premisave.auth.entity.ApplicationDocument;
import com.premisave.auth.entity.HomeOwnerApplication;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.ApplicationEventType;
import com.premisave.auth.enums.ApplicationStatus;
import com.premisave.auth.enums.DocumentStatus;
import com.premisave.auth.enums.DocumentType;
import com.premisave.auth.enums.Role;
import com.premisave.auth.exception.ApiException;
import com.premisave.auth.repository.HomeOwnerApplicationRepository;
import com.premisave.auth.service.application.ApplicationFileValidator.Detected;
import com.premisave.auth.service.application.DriveStorageService.StoredFile;
import com.premisave.auth.service.application.DriveStorageService.StoredFolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** What an applicant can do with their own application. */
@Slf4j
@Service
public class HomeOwnerApplicationService {

    public static final String BASE_PATH = "/home-owner/applications";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<ApplicationStatus> OPEN_STATUSES = Arrays.stream(ApplicationStatus.values())
            .filter(ApplicationStatus::isOpen).toList();

    /** A file ready to stream to the browser. */
    public record DownloadedDocument(String fileName, String mimeType, byte[] content) {
    }

    private final HomeOwnerApplicationRepository repository;
    private final ApplicationNumberGenerator numberGenerator;
    private final DriveStorageService drive;
    private final ApplicationEmailService emails;
    private final ApplicationMapper mapper;
    private final CurrentUser currentUser;
    private final long maxFileBytes;
    private final String maxFileLabel;
    private final int maxDocuments;

    public HomeOwnerApplicationService(HomeOwnerApplicationRepository repository,
                                       ApplicationNumberGenerator numberGenerator,
                                       DriveStorageService drive,
                                       ApplicationEmailService emails,
                                       ApplicationMapper mapper,
                                       CurrentUser currentUser,
                                       @Value("${home-owner-application.max-file-size-mb:8}") int maxFileSizeMb,
                                       @Value("${home-owner-application.max-documents:25}") int maxDocuments) {
        this.repository = repository;
        this.numberGenerator = numberGenerator;
        this.drive = drive;
        this.emails = emails;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.maxFileBytes = maxFileSizeMb * 1024L * 1024L;
        this.maxFileLabel = maxFileSizeMb + "MB";
        this.maxDocuments = maxDocuments;
    }

    // ------------------------------------------------------------------
    // Starting and editing
    // ------------------------------------------------------------------

    public Eligibility eligibility() {
        return eligibilityFor(currentUser.require());
    }

    public ApplicationResponse start(ApplicationRequest request) {
        User user = currentUser.require();

        Eligibility eligibility = eligibilityFor(user);
        if (!eligibility.canApply()) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, eligibility.reason(), eligibility);
        }

        HomeOwnerApplication app = new HomeOwnerApplication();
        app.setApplicationNumber(numberGenerator.next());
        app.setApplicantId(user.getId());
        app.setApplicantEmail(user.getEmail());
        app.setApplicantName(CurrentUser.fullName(user));
        app.setStatus(ApplicationStatus.DRAFT);

        // Save the applicant typing the same things twice
        String profileCountry = ApplicationValidation.countryCodeOrNull(user.getCountry());
        if (profileCountry != null) {
            app.setCountry(profileCountry);
        }
        if (user.getAddress1() != null && !user.getAddress1().isBlank()) {
            app.setPhysicalAddress(user.getAddress1().trim());
        }
        if (user.getPhoneNumber() != null) {
            try {
                app.setPhoneNumber(ApplicationValidation.normalizePhone(user.getPhoneNumber()));
            } catch (ApiException ignored) {
                // A malformed profile phone is simply not pre-filled
            }
        }

        if (request != null) {
            apply(app, request);
        }

        ApplicationEvents.add(app, ApplicationEventType.STARTED, null, ApplicationStatus.DRAFT, user, true,
                "Application started");
        HomeOwnerApplication saved = repository.save(app);
        log.info("Home owner application {} started by user {}", saved.getApplicationNumber(), user.getId());
        return view(saved);
    }

    public ApplicationResponse update(String id, ApplicationRequest request) {
        User user = currentUser.require();
        HomeOwnerApplication app = loadOwned(id, user);
        requireEditable(app);
        apply(app, request);
        return view(repository.save(app));
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /** The open application if there is one, otherwise the most recent. */
    public ApplicationResponse getMine() {
        User user = currentUser.require();
        List<HomeOwnerApplication> all = repository.findByApplicantIdOrderByCreatedAtDesc(user.getId());
        if (all.isEmpty()) {
            throw ApiException.notFound("You have not started a Home Owner application yet.");
        }
        HomeOwnerApplication chosen = all.stream()
                .filter(a -> a.getStatus().isOpen())
                .findFirst()
                .orElse(all.get(0));
        return view(chosen);
    }

    public List<ApplicationSummary> listMine() {
        User user = currentUser.require();
        return repository.findByApplicantIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(mapper::toSummary)
                .toList();
    }

    public ApplicationResponse get(String id) {
        return view(loadOwned(id, currentUser.require()));
    }

    // ------------------------------------------------------------------
    // Documents
    // ------------------------------------------------------------------

    public ApplicationResponse uploadDocument(String id, DocumentType type, MultipartFile file,
                                              String documentNumber, LocalDate expiryDate) {
        User user = currentUser.require();
        HomeOwnerApplication app = loadOwned(id, user);
        requireEditable(app);

        if (type == null) {
            throw ApiException.badRequest("Choose which document you are uploading.");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Please choose a file to upload.");
        }
        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            throw ApiException.badRequest("This document has already expired. Please upload a current one.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("We could not read that file. Please try again.");
        }
        Detected detected = ApplicationFileValidator.validate(bytes, maxFileBytes, maxFileLabel);
        String hash = sha256(bytes);

        List<ApplicationDocument> docs = app.getDocuments();
        if (docs.size() >= maxDocuments) {
            throw ApiException.conflict("You have reached the limit of " + maxDocuments
                    + " files. Remove one before adding another.");
        }
        for (ApplicationDocument existing : docs) {
            if (hash.equals(existing.getSha256())) {
                throw ApiException.conflict("This exact file is already attached as \""
                        + existing.getType().getLabel() + "\".");
            }
        }

        List<ApplicationDocument> sameType = docs.stream().filter(d -> d.getType() == type).toList();
        List<ApplicationDocument> toReplace = new ArrayList<>();
        if (type.isAllowsMultiple()) {
            long active = sameType.stream().filter(d -> d.getStatus() != DocumentStatus.REJECTED).count();
            if (active >= type.maxFiles()) {
                throw ApiException.conflict("You can attach up to " + type.maxFiles() + " files for \""
                        + type.getLabel() + "\". Remove one first.");
            }
        } else {
            if (sameType.stream().anyMatch(d -> d.getStatus() == DocumentStatus.VERIFIED)) {
                throw ApiException.conflict("\"" + type.getLabel()
                        + "\" is already verified and cannot be replaced.");
            }
            toReplace.addAll(sameType);
        }

        ensureDriveFolder(app);

        String storedName = type.name() + "_" + LocalDateTime.now().format(STAMP) + "." + detected.extension();
        StoredFile uploaded = drive.upload(app.getDriveFolderId(), storedName, detected.mimeType(), bytes);

        ApplicationDocument doc = new ApplicationDocument();
        doc.setType(type);
        doc.setOriginalFileName(ApplicationFileValidator.cleanFileName(file.getOriginalFilename()));
        doc.setStoredFileName(storedName);
        doc.setMimeType(detected.mimeType());
        doc.setSizeBytes(bytes.length);
        doc.setSha256(hash);
        doc.setDriveFileId(uploaded.id());
        doc.setDriveViewLink(uploaded.webViewLink());
        doc.setDocumentNumber(ApplicationValidation.merge(documentNumber, null));
        doc.setExpiryDate(expiryDate);

        docs.removeAll(toReplace);
        docs.add(doc);

        ApplicationEvents.add(app, ApplicationEventType.DOCUMENT_UPLOADED, app.getStatus(), app.getStatus(), user, true,
                type.getLabel() + (toReplace.isEmpty() ? " uploaded" : " replaced"));
        HomeOwnerApplication saved = repository.save(app);

        // Only after the new file is safely recorded do we remove the old ones from Drive
        toReplace.forEach(old -> drive.delete(old.getDriveFileId()));
        return view(saved);
    }

    public ApplicationResponse deleteDocument(String id, String documentId) {
        User user = currentUser.require();
        HomeOwnerApplication app = loadOwned(id, user);
        requireEditable(app);

        ApplicationDocument doc = findDocument(app, documentId);
        if (doc.getStatus() == DocumentStatus.VERIFIED) {
            throw ApiException.conflict("This document is already verified and cannot be removed.");
        }

        app.getDocuments().remove(doc);
        ApplicationEvents.add(app, ApplicationEventType.DOCUMENT_REMOVED, app.getStatus(), app.getStatus(), user, true,
                doc.getType().getLabel() + " removed");
        HomeOwnerApplication saved = repository.save(app);
        drive.delete(doc.getDriveFileId());
        return view(saved);
    }

    /** Owner or any staff member may download. Everyone else gets a 404. */
    public DownloadedDocument downloadDocument(String id, String documentId) {
        User user = currentUser.require();
        HomeOwnerApplication app = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Application not found."));
        boolean owner = user.getId().equals(app.getApplicantId());
        if (!owner && !CurrentUser.isStaff(user)) {
            throw ApiException.notFound("Application not found.");
        }
        ApplicationDocument doc = findDocument(app, documentId);
        byte[] content = drive.download(doc.getDriveFileId());
        return new DownloadedDocument(doc.getOriginalFileName(), doc.getMimeType(), content);
    }

    // ------------------------------------------------------------------
    // Submitting, withdrawing, deleting
    // ------------------------------------------------------------------

    public ApplicationResponse submit(String id) {
        User user = currentUser.require();
        HomeOwnerApplication app = loadOwned(id, user);

        ApplicationStatus from = app.getStatus();
        if (from != ApplicationStatus.DRAFT && from != ApplicationStatus.INFO_REQUESTED) {
            throw ApiException.conflict("This application cannot be submitted while it is \""
                    + from.getLabel() + "\".");
        }

        ReadinessView readiness = ApplicationReadiness.evaluate(app);
        if (!readiness.canSubmit()) {
            throw ApiException.unprocessable("Your application is not complete yet. "
                    + readiness.issues().size() + " item(s) still need attention.", readiness.issues());
        }

        boolean resubmission = from == ApplicationStatus.INFO_REQUESTED;
        LocalDateTime now = LocalDateTime.now();
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setSubmissionCount(app.getSubmissionCount() + 1);
        app.setLastSubmittedAt(now);
        if (app.getSubmittedAt() == null) {
            app.setSubmittedAt(now);
        }
        app.setLatestReviewerMessage(null);

        ApplicationEvents.add(app, resubmission ? ApplicationEventType.RESUBMITTED : ApplicationEventType.SUBMITTED,
                from, ApplicationStatus.SUBMITTED, user, true,
                resubmission ? "Updates submitted for review" : "Application submitted");
        HomeOwnerApplication saved = repository.save(app);
        emails.sendSubmitted(saved, resubmission);
        return view(saved);
    }

    public ApplicationResponse withdraw(String id, String reason) {
        User user = currentUser.require();
        HomeOwnerApplication app = loadOwned(id, user);

        ApplicationStatus from = app.getStatus();
        if (!from.isOpen()) {
            throw ApiException.conflict("This application is already closed (" + from.getLabel() + ").");
        }

        String message = reason == null || reason.isBlank() ? "Withdrawn by the applicant" : reason.trim();
        app.setStatus(ApplicationStatus.WITHDRAWN);
        app.setDecidedAt(LocalDateTime.now());
        app.setDecisionReason(message);
        ApplicationEvents.add(app, ApplicationEventType.WITHDRAWN, from, ApplicationStatus.WITHDRAWN, user, true, message);
        HomeOwnerApplication saved = repository.save(app);

        if (from != ApplicationStatus.DRAFT) {
            emails.sendStatusChanged(saved, from, null);
        }
        return view(saved);
    }

    /** Removes a draft that was never submitted, along with its files in Drive. */
    public void deleteDraft(String id) {
        User user = currentUser.require();
        HomeOwnerApplication app = loadOwned(id, user);
        if (app.getStatus() != ApplicationStatus.DRAFT || app.getSubmissionCount() > 0) {
            throw ApiException.conflict("Only a draft that was never submitted can be deleted. "
                    + "Withdraw the application instead.");
        }
        // Deleting the folder removes every file inside it
        drive.delete(app.getDriveFolderId());
        repository.delete(app);
        log.info("Draft application {} deleted by its owner", app.getApplicationNumber());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Eligibility eligibilityFor(User user) {
        if (user.getRole() == Role.HOME_OWNER) {
            return new Eligibility(false, "Your account is already a Home Owner account.", null, null);
        }
        if (user.getRole() != Role.CLIENT) {
            return new Eligibility(false, "Staff accounts cannot apply to become Home Owners.", null, null);
        }
        Optional<HomeOwnerApplication> open = repository.findFirstByApplicantIdAndStatusIn(user.getId(), OPEN_STATUSES);
        if (open.isPresent()) {
            return new Eligibility(false, "You already have an application in progress.",
                    open.get().getId(), open.get().getStatus());
        }
        return new Eligibility(true, null, null, null);
    }

    private ApplicationResponse view(HomeOwnerApplication app) {
        return mapper.toResponse(app, false, BASE_PATH);
    }

    private HomeOwnerApplication loadOwned(String id, User user) {
        return repository.findById(id)
                .filter(a -> user.getId().equals(a.getApplicantId()))
                .orElseThrow(() -> ApiException.notFound("Application not found."));
    }

    private void requireEditable(HomeOwnerApplication app) {
        if (!app.getStatus().isApplicantEditable()) {
            throw ApiException.conflict("This application cannot be edited while it is \""
                    + app.getStatus().getLabel() + "\".");
        }
    }

    private ApplicationDocument findDocument(HomeOwnerApplication app, String documentId) {
        return app.getDocuments().stream()
                .filter(d -> d.getId().equals(documentId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Document not found."));
    }

    private void ensureDriveFolder(HomeOwnerApplication app) {
        if (app.getDriveFolderId() != null) {
            return;
        }
        StoredFolder folder = drive.createApplicationFolder(app.getApplicationNumber() + " " + app.getApplicantName());
        app.setDriveFolderId(folder.id());
        app.setDriveFolderUrl(folder.webViewLink());
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Copies the fields that were sent. null means "leave as is", blank text means "clear". */
    private void apply(HomeOwnerApplication app, ApplicationRequest r) {
        if (r.getOwnerType() != null) {
            app.setOwnerType(r.getOwnerType());
        }
        app.setCompanyName(ApplicationValidation.merge(r.getCompanyName(), app.getCompanyName()));
        app.setCompanyRegistrationNumber(
                ApplicationValidation.merge(r.getCompanyRegistrationNumber(), app.getCompanyRegistrationNumber()));

        if (r.getIdType() != null) {
            app.setIdType(r.getIdType());
        }
        app.setIdNumber(ApplicationValidation.upper(ApplicationValidation.merge(r.getIdNumber(), app.getIdNumber())));
        if (r.getIdIssuingCountry() != null) {
            app.setIdIssuingCountry(ApplicationValidation.countryCode(r.getIdIssuingCountry(), "ID issuing country"));
        }
        app.setTaxId(ApplicationValidation.upper(ApplicationValidation.merge(r.getTaxId(), app.getTaxId())));
        if (r.getDateOfBirth() != null) {
            if (!ApplicationValidation.isAdult(r.getDateOfBirth())) {
                throw ApiException.badRequest("You must be at least 18 years old to apply.");
            }
            app.setDateOfBirth(r.getDateOfBirth());
        }
        if (r.getNationality() != null) {
            app.setNationality(ApplicationValidation.countryCode(r.getNationality(), "Nationality"));
        }

        if (r.getPhoneNumber() != null) {
            app.setPhoneNumber(ApplicationValidation.normalizePhone(r.getPhoneNumber(), "Phone number"));
        }
        if (r.getAlternatePhoneNumber() != null) {
            app.setAlternatePhoneNumber(
                    ApplicationValidation.normalizePhone(r.getAlternatePhoneNumber(), "Alternate phone number"));
        }
        if (r.getCountry() != null) {
            app.setCountry(ApplicationValidation.countryCode(r.getCountry(), "Country"));
        }
        app.setRegion(ApplicationValidation.merge(r.getRegion(), app.getRegion()));
        app.setCity(ApplicationValidation.merge(r.getCity(), app.getCity()));
        app.setPhysicalAddress(ApplicationValidation.merge(r.getPhysicalAddress(), app.getPhysicalAddress()));
        app.setPostalAddress(ApplicationValidation.merge(r.getPostalAddress(), app.getPostalAddress()));

        if (r.getNumberOfProperties() != null) {
            app.setNumberOfProperties(r.getNumberOfProperties());
        }
        if (r.getEstimatedTotalUnits() != null) {
            app.setEstimatedTotalUnits(r.getEstimatedTotalUnits());
        }
        if (r.getPropertyTypes() != null) {
            app.setPropertyTypes(new ArrayList<>(r.getPropertyTypes().stream().distinct().toList()));
        }
        app.setPrimaryPropertyLocation(
                ApplicationValidation.merge(r.getPrimaryPropertyLocation(), app.getPrimaryPropertyLocation()));
        app.setPropertyDescription(ApplicationValidation.merge(r.getPropertyDescription(), app.getPropertyDescription()));
        if (r.getManagementPreference() != null) {
            app.setManagementPreference(r.getManagementPreference());
        }
        if (r.getYearsAsLandlord() != null) {
            app.setYearsAsLandlord(r.getYearsAsLandlord());
        }

        if (r.getPayoutMethod() != null) {
            app.setPayoutMethod(r.getPayoutMethod());
        }
        app.setMobileMoneyProvider(ApplicationValidation.merge(r.getMobileMoneyProvider(), app.getMobileMoneyProvider()));
        if (r.getMobileMoneyNumber() != null) {
            app.setMobileMoneyNumber(ApplicationValidation.normalizePhone(r.getMobileMoneyNumber(), "Mobile money number"));
        }
        app.setPaypalEmail(ApplicationValidation.merge(r.getPaypalEmail(), app.getPaypalEmail()));
        app.setBankName(ApplicationValidation.merge(r.getBankName(), app.getBankName()));
        app.setBankAccountName(ApplicationValidation.merge(r.getBankAccountName(), app.getBankAccountName()));
        app.setBankAccountNumber(ApplicationValidation.upper(
                ApplicationValidation.merge(r.getBankAccountNumber(), app.getBankAccountNumber())));
        app.setBankSwiftCode(ApplicationValidation.upper(
                ApplicationValidation.merge(r.getBankSwiftCode(), app.getBankSwiftCode())));
        app.setBankBranch(ApplicationValidation.merge(r.getBankBranch(), app.getBankBranch()));

        app.setMotivation(ApplicationValidation.merge(r.getMotivation(), app.getMotivation()));
        if (r.getReferralSource() != null) {
            app.setReferralSource(r.getReferralSource());
        }

        if (r.getTermsAccepted() != null) {
            app.setTermsAccepted(r.getTermsAccepted());
        }
        if (r.getPrivacyConsent() != null) {
            app.setPrivacyConsent(r.getPrivacyConsent());
        }
        if (r.getDeclarationAccepted() != null) {
            app.setDeclarationAccepted(r.getDeclarationAccepted());
        }
        if (app.isTermsAccepted() && app.isPrivacyConsent() && app.isDeclarationAccepted()) {
            if (app.getConsentedAt() == null) {
                app.setConsentedAt(LocalDateTime.now());
            }
        } else {
            app.setConsentedAt(null);
        }
    }
}