package com.premisave.auth.service.application;

import com.premisave.auth.dto.application.ApplicationDtos.ApplicationResponse;
import com.premisave.auth.dto.application.ApplicationDtos.ApplicationSummary;
import com.premisave.auth.dto.application.ApplicationDtos.PageResponse;
import com.premisave.auth.dto.application.ApplicationDtos.PromoteResponse;
import com.premisave.auth.dto.application.ApplicationDtos.ReadinessView;
import com.premisave.auth.dto.application.ApplicationDtos.StaffMember;
import com.premisave.auth.dto.application.ApplicationDtos.StatsResponse;
import com.premisave.auth.dto.application.ApplicationRequests.DocumentReview;
import com.premisave.auth.dto.application.ApplicationRequests.Promote;
import com.premisave.auth.dto.application.ApplicationRequests.StatusChange;
import com.premisave.auth.entity.ApplicationDocument;
import com.premisave.auth.entity.HomeOwnerApplication;
import com.premisave.auth.entity.ReviewNote;
import com.premisave.auth.entity.User;
import com.premisave.auth.enums.ApplicationEventType;
import com.premisave.auth.enums.ApplicationStatus;
import com.premisave.auth.enums.DocumentStatus;
import com.premisave.auth.enums.OwnerType;
import com.premisave.auth.enums.Role;
import com.premisave.auth.exception.ApiException;
import com.premisave.auth.repository.HomeOwnerApplicationRepository;
import com.premisave.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What staff can do with applications: find them, claim them, review documents,
 * move them through the statuses, and (admins only) promote the applicant.
 */
@Slf4j
@Service
public class ApplicationReviewService {

    public static final String BASE_PATH = "/staff/applications";

    /** Where a reviewer may move an application. ACCEPTED is reached only through promote. */
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = new EnumMap<>(ApplicationStatus.class);

    static {
        TRANSITIONS.put(ApplicationStatus.SUBMITTED, EnumSet.of(
                ApplicationStatus.PROCESSING, ApplicationStatus.INFO_REQUESTED, ApplicationStatus.REJECTED));
        TRANSITIONS.put(ApplicationStatus.PROCESSING, EnumSet.of(
                ApplicationStatus.INFO_REQUESTED, ApplicationStatus.VERIFIED, ApplicationStatus.REJECTED));
        TRANSITIONS.put(ApplicationStatus.INFO_REQUESTED, EnumSet.of(ApplicationStatus.REJECTED));
        TRANSITIONS.put(ApplicationStatus.VERIFIED, EnumSet.of(
                ApplicationStatus.PROCESSING, ApplicationStatus.INFO_REQUESTED, ApplicationStatus.REJECTED));
    }

    private static final List<ApplicationStatus> WORKING_STATUSES = List.of(
            ApplicationStatus.SUBMITTED, ApplicationStatus.PROCESSING, ApplicationStatus.VERIFIED);
    private static final List<ApplicationStatus> OPEN_STATUSES = Arrays.stream(ApplicationStatus.values())
            .filter(ApplicationStatus::isOpen).toList();

    private final HomeOwnerApplicationRepository repository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationMapper mapper;
    private final ApplicationEmailService emails;
    private final CurrentUser currentUser;

    public ApplicationReviewService(HomeOwnerApplicationRepository repository,
                                    UserRepository userRepository,
                                    MongoTemplate mongoTemplate,
                                    RedisTemplate<String, Object> redisTemplate,
                                    ApplicationMapper mapper,
                                    ApplicationEmailService emails,
                                    CurrentUser currentUser) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
        this.emails = emails;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------------
    // Queue and dashboard
    // ------------------------------------------------------------------

    public PageResponse<ApplicationSummary> search(String q, List<ApplicationStatus> statuses, OwnerType ownerType,
                                                   String country, String region, String assignee, String sort, int page, int size) {
        User me = currentUser.requireStaff();

        List<Criteria> filters = new ArrayList<>();
        if (statuses == null || statuses.isEmpty()) {
            // Drafts are private to the applicant until they submit
            filters.add(Criteria.where("status").ne(ApplicationStatus.DRAFT));
        } else {
            filters.add(Criteria.where("status").in(statuses));
        }
        if (ownerType != null) {
            filters.add(Criteria.where("ownerType").is(ownerType));
        }
        if (country != null && !country.isBlank()) {
            filters.add(Criteria.where("country").is(country.trim().toUpperCase()));
        }
        if (region != null && !region.isBlank()) {
            filters.add(Criteria.where("region").regex(exact(region.trim())));
        }
        if (assignee != null && !assignee.isBlank()) {
            String value = assignee.trim();
            if (value.equalsIgnoreCase("me")) {
                filters.add(Criteria.where("assignedReviewerId").is(me.getId()));
            } else if (value.equalsIgnoreCase("none")) {
                filters.add(Criteria.where("assignedReviewerId").is(null));
            } else {
                filters.add(Criteria.where("assignedReviewerId").is(value));
            }
        }
        if (q != null && !q.isBlank()) {
            Pattern contains = Pattern.compile(Pattern.quote(q.trim()), Pattern.CASE_INSENSITIVE);
            filters.add(new Criteria().orOperator(
                    Criteria.where("applicationNumber").regex(contains),
                    Criteria.where("applicantName").regex(contains),
                    Criteria.where("applicantEmail").regex(contains),
                    Criteria.where("phoneNumber").regex(contains),
                    Criteria.where("idNumber").regex(contains),
                    Criteria.where("taxId").regex(contains),
                    Criteria.where("companyName").regex(contains)));
        }

        Criteria criteria = new Criteria().andOperator(filters.toArray(new Criteria[0]));

        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Sort ordering = switch (sort == null ? "" : sort.toLowerCase()) {
            case "newest" -> Sort.by(Sort.Direction.DESC, "submittedAt");
            case "updated" -> Sort.by(Sort.Direction.DESC, "updatedAt");
            default -> Sort.by(Sort.Direction.ASC, "submittedAt"); // oldest first: longest waiting on top
        };

        long total = mongoTemplate.count(new Query(criteria), HomeOwnerApplication.class);
        List<ApplicationSummary> content = mongoTemplate
                .find(new Query(criteria).with(PageRequest.of(safePage, safeSize, ordering)), HomeOwnerApplication.class)
                .stream().map(mapper::toSummary).toList();

        int totalPages = (int) Math.ceil(total / (double) safeSize);
        return new PageResponse<>(content, safePage, safeSize, total, totalPages);
    }

    public StatsResponse stats() {
        User me = currentUser.requireStaff();

        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        long total = 0;
        for (ApplicationStatus s : ApplicationStatus.values()) {
            long count = repository.countByStatus(s);
            byStatus.put(s, count);
            if (s != ApplicationStatus.DRAFT) {
                total += count;
            }
        }

        long unassigned = mongoTemplate.count(new Query(new Criteria().andOperator(
                Criteria.where("status").in(List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.PROCESSING)),
                Criteria.where("assignedReviewerId").is(null))), HomeOwnerApplication.class);
        long mine = mongoTemplate.count(new Query(new Criteria().andOperator(
                Criteria.where("status").in(WORKING_STATUSES),
                Criteria.where("assignedReviewerId").is(me.getId()))), HomeOwnerApplication.class);

        // Average time from submission to decision over the latest decisions (withdrawals do not count)
        double totalHours = 0;
        int counted = 0;
        for (HomeOwnerApplication a : repository.findTop200ByDecidedAtNotNullOrderByDecidedAtDesc()) {
            if (a.getStatus() == ApplicationStatus.WITHDRAWN || a.getSubmittedAt() == null) {
                continue;
            }
            totalHours += Duration.between(a.getSubmittedAt(), a.getDecidedAt()).toMinutes() / 60.0;
            counted++;
        }
        Double average = counted == 0 ? null : Math.round(totalHours / counted * 10.0) / 10.0;

        HomeOwnerApplication oldest = repository
                .findFirstByStatusInOrderBySubmittedAtAsc(List.of(ApplicationStatus.SUBMITTED)).orElse(null);

        return new StatsResponse(
                total, byStatus,
                byStatus.get(ApplicationStatus.SUBMITTED), unassigned, mine,
                byStatus.get(ApplicationStatus.INFO_REQUESTED), byStatus.get(ApplicationStatus.VERIFIED),
                average,
                oldest == null ? null : oldest.getSubmittedAt(),
                oldest == null ? null : oldest.getId());
    }

    /** Active staff who can be given an application. */
    public List<StaffMember> reviewers() {
        currentUser.requireStaff();
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("role").in(CurrentUser.STAFF_ROLES),
                Criteria.where("active").is(true)));
        return mongoTemplate.find(query, User.class).stream()
                .map(u -> new StaffMember(u.getId(), CurrentUser.fullName(u), u.getEmail(), u.getRole().name()))
                .sorted(Comparator.comparing(StaffMember::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public ApplicationResponse get(String id) {
        currentUser.requireStaff();
        return view(load(id));
    }

    // ------------------------------------------------------------------
    // Working an application
    // ------------------------------------------------------------------

    /** Take an application: assigns it to me and, if it was waiting, starts the review. */
    public ApplicationResponse claim(String id) {
        User me = currentUser.requireStaff();
        HomeOwnerApplication app = load(id);
        requireWorkable(app);

        boolean started = false;
        ApplicationStatus from = app.getStatus();
        if (from == ApplicationStatus.SUBMITTED) {
            startReview(app, me);
            started = true;
        }
        if (!me.getId().equals(app.getAssignedReviewerId())) {
            assignTo(app, me, me);
        }
        HomeOwnerApplication saved = repository.save(app);
        if (started) {
            emails.sendStatusChanged(saved, from, null);
        }
        return view(saved);
    }

    /** Give an application to a colleague. Admins can assign anyone; others can only take it themselves. */
    public ApplicationResponse assign(String id, String reviewerId) {
        User me = currentUser.requireStaff();
        HomeOwnerApplication app = load(id);
        requireWorkable(app);

        User reviewer = userRepository.findById(reviewerId)
                .filter(u -> CurrentUser.isStaff(u) && u.isActive())
                .orElseThrow(() -> ApiException.badRequest("That person is not an active staff member."));
        if (!reviewer.getId().equals(me.getId()) && me.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Only an administrator can assign an application to someone else.");
        }
        assignTo(app, reviewer, me);
        return view(repository.save(app));
    }

    public ApplicationResponse changeStatus(String id, StatusChange change) {
        User me = currentUser.requireStaff();
        HomeOwnerApplication app = load(id);
        ApplicationStatus from = app.getStatus();
        ApplicationStatus to = change.getStatus();
        String message = clean(change.getMessage());

        if (to == ApplicationStatus.ACCEPTED) {
            throw ApiException.badRequest("An application is accepted by promoting the user to Home Owner. "
                    + "Use the Promote action, which only an administrator can run.");
        }
        if (to == from) {
            throw ApiException.conflict("The application is already \"" + from.getLabel() + "\".");
        }

        boolean reopen = from == ApplicationStatus.REJECTED && to == ApplicationStatus.PROCESSING;
        if (reopen) {
            currentUser.requireAdmin();
            repository.findFirstByApplicantIdAndStatusIn(app.getApplicantId(), OPEN_STATUSES)
                    .filter(other -> !other.getId().equals(app.getId()))
                    .ifPresent(other -> {
                        throw ApiException.conflict("The applicant already has another open application ("
                                + other.getApplicationNumber() + "), so this one cannot be reopened.");
                    });
        } else {
            Set<ApplicationStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
            if (!allowed.contains(to)) {
                String options = allowed.isEmpty() ? "none, this application is \"" + from.getLabel() + "\""
                        : allowed.stream().map(ApplicationStatus::getLabel).toList().toString();
                throw ApiException.conflict("An application that is \"" + from.getLabel()
                        + "\" cannot be moved to \"" + to.getLabel() + "\". Allowed next steps: " + options + ".");
            }
        }

        if ((to == ApplicationStatus.INFO_REQUESTED || to == ApplicationStatus.REJECTED) && message == null) {
            throw ApiException.badRequest(to == ApplicationStatus.INFO_REQUESTED
                    ? "Tell the applicant what they need to fix or add."
                    : "Give the applicant a reason for the rejection.");
        }

        if (to == ApplicationStatus.VERIFIED) {
            ReadinessView readiness = ApplicationReadiness.evaluate(app);
            if (!readiness.canSubmit()) {
                throw ApiException.unprocessable("The application still has missing details, so it cannot be verified.",
                        readiness.issues());
            }
            if (!readiness.allRequiredVerified()) {
                throw ApiException.unprocessable(
                        "Verify every required document first, then mark the application verified.",
                        readiness.requirements().stream()
                                .filter(r -> r.required() && !r.verified())
                                .map(r -> new com.premisave.auth.dto.application.ApplicationDtos.FieldIssue(
                                        "documents." + r.key(), r.label() + " is not verified yet"))
                                .toList());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        app.setStatus(to);
        switch (to) {
            case PROCESSING -> {
                if (app.getReviewStartedAt() == null) {
                    app.setReviewStartedAt(now);
                }
                if (reopen) {
                    app.setDecidedAt(null);
                    app.setDecisionReason(null);
                }
                app.setLatestReviewerMessage(message);
                if (app.getAssignedReviewerId() == null) {
                    assignTo(app, me, me);
                }
            }
            case REJECTED -> {
                app.setDecidedAt(now);
                app.setDecisionReason(message);
                app.setLatestReviewerMessage(message);
            }
            default -> app.setLatestReviewerMessage(message);
        }
        if (app.getAssignedReviewerId() == null && to != ApplicationStatus.REJECTED) {
            assignTo(app, me, me);
        }

        ApplicationEvents.add(app, ApplicationEventType.STATUS_CHANGED, from, to, me, false,
                message != null ? message : "Status changed to " + to.getLabel());
        addInternalNote(app, me, clean(change.getInternalNote()));

        HomeOwnerApplication saved = repository.save(app);
        emails.sendStatusChanged(saved, from, message);
        log.info("Application {} moved {} to {} by {}", saved.getApplicationNumber(), from, to, me.getId());
        return view(saved);
    }

    public ApplicationResponse reviewDocument(String id, String documentId, DocumentReview review) {
        User me = currentUser.requireStaff();
        HomeOwnerApplication app = load(id);
        requireWorkable(app);

        if (review.getStatus() == DocumentStatus.PENDING) {
            throw ApiException.badRequest("A document can be marked VERIFIED or REJECTED.");
        }
        ApplicationDocument doc = app.getDocuments().stream()
                .filter(d -> d.getId().equals(documentId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Document not found."));

        String reason = clean(review.getReason());
        boolean reject = review.getStatus() == DocumentStatus.REJECTED;
        if (reject && reason == null) {
            throw ApiException.badRequest("Tell the applicant what is wrong with this file so they can replace it.");
        }

        ApplicationStatus from = app.getStatus();
        boolean startedReview = false;
        if (from == ApplicationStatus.SUBMITTED) {
            startReview(app, me);
            startedReview = true;
        }
        if (app.getAssignedReviewerId() == null) {
            assignTo(app, me, me);
        }

        doc.setStatus(review.getStatus());
        doc.setReviewReason(reject ? reason : null);
        doc.setReviewedById(me.getId());
        doc.setReviewedByName(CurrentUser.fullName(me));
        doc.setReviewedAt(LocalDateTime.now());

        String label = doc.getType().getLabel();
        ApplicationEvents.add(app, ApplicationEventType.DOCUMENT_REVIEWED, app.getStatus(), app.getStatus(), me, false,
                reject ? label + " needs replacing: " + reason : label + " verified");

        // A rejected file means the application can no longer be called verified
        ApplicationStatus afterDoc = app.getStatus();
        if (reject && review.isRequestReupload()
                && (afterDoc == ApplicationStatus.PROCESSING || afterDoc == ApplicationStatus.VERIFIED)) {
            app.setStatus(ApplicationStatus.INFO_REQUESTED);
            app.setLatestReviewerMessage(label + " needs replacing: " + reason);
            ApplicationEvents.add(app, ApplicationEventType.STATUS_CHANGED, afterDoc, ApplicationStatus.INFO_REQUESTED,
                    me, false, app.getLatestReviewerMessage());
        } else if (reject && afterDoc == ApplicationStatus.VERIFIED) {
            app.setStatus(ApplicationStatus.PROCESSING);
            ApplicationEvents.add(app, ApplicationEventType.STATUS_CHANGED, afterDoc, ApplicationStatus.PROCESSING,
                    me, false, "Back in review because a document needs replacing");
        }

        HomeOwnerApplication saved = repository.save(app);

        if (saved.getStatus() == ApplicationStatus.INFO_REQUESTED && afterDoc != ApplicationStatus.INFO_REQUESTED) {
            // One email that carries the reason and asks the applicant to resubmit
            emails.sendStatusChanged(saved, afterDoc, saved.getLatestReviewerMessage());
        } else {
            if (startedReview && !reject) {
                emails.sendStatusChanged(saved, from, null);
            }
            emails.sendDocumentReviewed(saved, doc);
        }
        return view(saved);
    }

    public ApplicationResponse addNote(String id, String text) {
        User me = currentUser.requireStaff();
        HomeOwnerApplication app = load(id);
        addInternalNote(app, me, clean(text));
        return view(repository.save(app));
    }

    // ------------------------------------------------------------------
    // The big button: Client becomes Home Owner
    // ------------------------------------------------------------------

    public PromoteResponse promote(String id, Promote body) {
        User admin = currentUser.requireAdmin();
        HomeOwnerApplication app = load(id);

        if (app.getStatus() == ApplicationStatus.ACCEPTED) {
            throw ApiException.conflict("This applicant has already been promoted.");
        }
        if (app.getStatus() != ApplicationStatus.VERIFIED) {
            throw ApiException.conflict("Only a verified application can be promoted. This one is \""
                    + app.getStatus().getLabel() + "\".");
        }
        ReadinessView readiness = ApplicationReadiness.evaluate(app);
        if (!readiness.canSubmit() || !readiness.allRequiredVerified()) {
            throw ApiException.unprocessable(
                    "Some details or documents are no longer verified. Review the application again.",
                    readiness.issues());
        }

        User applicant = userRepository.findById(app.getApplicantId())
                .orElseThrow(() -> ApiException.notFound("The applicant's account no longer exists."));
        if (!applicant.isActive() || applicant.isArchived()) {
            throw ApiException.conflict("The applicant's account is not active.");
        }
        if (applicant.getRole() == Role.HOME_OWNER) {
            throw ApiException.conflict("This account is already a Home Owner account.");
        }
        if (applicant.getRole() != Role.CLIENT) {
            throw ApiException.conflict("Only a Client account can be promoted. This account is "
                    + applicant.getRole() + ".");
        }

        Role previous = applicant.getRole();
        String message = clean(body == null ? null : body.getMessage());

        applicant.setRole(Role.HOME_OWNER);
        userRepository.save(applicant);

        ApplicationStatus from = app.getStatus();
        LocalDateTime now = LocalDateTime.now();
        app.setStatus(ApplicationStatus.ACCEPTED);
        app.setDecidedAt(now);
        app.setDecisionReason(message);
        app.setLatestReviewerMessage(message);
        ApplicationEvents.add(app, ApplicationEventType.PROMOTED, from, ApplicationStatus.ACCEPTED, admin, false,
                "Approved. Your account is now a Home Owner account.");
        addInternalNote(app, admin, clean(body == null ? null : body.getInternalNote()));

        HomeOwnerApplication saved;
        try {
            saved = repository.save(app);
        } catch (RuntimeException e) {
            // Keep the account and the application in step
            applicant.setRole(previous);
            userRepository.save(applicant);
            throw e;
        }

        // The auth filter reads the user from Redis first, so drop the stale copy now
        try {
            redisTemplate.delete("user:" + applicant.getId());
        } catch (RuntimeException e) {
            log.warn("Could not clear the cached user {}: {}", applicant.getId(), e.getMessage());
        }

        emails.sendPromoted(saved, message);
        log.info("User {} promoted CLIENT to HOME_OWNER by {} (application {})",
                applicant.getId(), admin.getId(), saved.getApplicationNumber());

        return new PromoteResponse(saved.getId(), saved.getApplicationNumber(), applicant.getId(),
                applicant.getEmail(), previous.name(), Role.HOME_OWNER.name(), saved.getStatus(),
                CurrentUser.fullName(applicant) + " is now a Home Owner. They were emailed. "
                        + "Their new access applies to new sign-ins and token refreshes.");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private HomeOwnerApplication load(String id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Application not found."));
    }

    private ApplicationResponse view(HomeOwnerApplication app) {
        return mapper.toResponse(app, true, BASE_PATH);
    }

    /** Staff work on submitted applications that are still open. */
    private void requireWorkable(HomeOwnerApplication app) {
        ApplicationStatus s = app.getStatus();
        if (s == ApplicationStatus.DRAFT) {
            throw ApiException.conflict("The applicant has not submitted this application yet.");
        }
        if (s.isFinalState()) {
            throw ApiException.conflict("This application is closed (" + s.getLabel() + ").");
        }
    }

    private void startReview(HomeOwnerApplication app, User me) {
        ApplicationStatus from = app.getStatus();
        app.setStatus(ApplicationStatus.PROCESSING);
        if (app.getReviewStartedAt() == null) {
            app.setReviewStartedAt(LocalDateTime.now());
        }
        ApplicationEvents.add(app, ApplicationEventType.STATUS_CHANGED, from, ApplicationStatus.PROCESSING, me, false,
                "A reviewer started on your application");
    }

    private void assignTo(HomeOwnerApplication app, User reviewer, User actor) {
        app.setAssignedReviewerId(reviewer.getId());
        app.setAssignedReviewerName(CurrentUser.fullName(reviewer));
        ApplicationEvents.add(app, ApplicationEventType.ASSIGNED, app.getStatus(), app.getStatus(), actor, false,
                "Assigned to a reviewer");
    }

    private void addInternalNote(HomeOwnerApplication app, User author, String text) {
        if (text == null) {
            return;
        }
        ReviewNote note = new ReviewNote();
        note.setAuthorId(author.getId());
        note.setAuthorName(CurrentUser.fullName(author));
        note.setAuthorRole(author.getRole() == null ? null : author.getRole().name());
        note.setText(text);
        if (app.getNotes() == null) {
            app.setNotes(new ArrayList<>());
        }
        app.getNotes().add(note);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Pattern exact(String value) {
        return Pattern.compile("^" + Pattern.quote(value) + "$", Pattern.CASE_INSENSITIVE);
    }
}