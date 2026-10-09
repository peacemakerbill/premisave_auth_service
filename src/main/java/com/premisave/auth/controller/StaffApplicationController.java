package com.premisave.auth.controller;

import com.premisave.auth.dto.application.ApplicationDtos.ApplicationResponse;
import com.premisave.auth.dto.application.ApplicationDtos.ApplicationSummary;
import com.premisave.auth.dto.application.ApplicationDtos.PageResponse;
import com.premisave.auth.dto.application.ApplicationDtos.StaffMember;
import com.premisave.auth.dto.application.ApplicationDtos.StatsResponse;
import com.premisave.auth.dto.application.ApplicationRequests.Assign;
import com.premisave.auth.dto.application.ApplicationRequests.DocumentReview;
import com.premisave.auth.dto.application.ApplicationRequests.Note;
import com.premisave.auth.dto.application.ApplicationRequests.StatusChange;
import com.premisave.auth.enums.ApplicationStatus;
import com.premisave.auth.enums.OwnerType;
import com.premisave.auth.service.application.ApplicationReviewService;
import com.premisave.auth.service.application.HomeOwnerApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Review desk for ADMIN, OPERATIONS, FINANCE and SUPPORT. Access is enforced
 * twice: by the security rules for /staff/** and again inside the services.
 */
@RestController
@RequestMapping("/staff/applications")
public class StaffApplicationController {

    private final ApplicationReviewService reviewService;
    private final HomeOwnerApplicationService applicantService;

    public StaffApplicationController(ApplicationReviewService reviewService,
                                      HomeOwnerApplicationService applicantService) {
        this.reviewService = reviewService;
        this.applicantService = applicantService;
    }

    /**
     * The review queue.
     * q: application number, name, email, phone, ID number, KRA PIN or company.
     * status: one or more, comma separated (default: everything except drafts).
     * assignee: "me", "none" or a staff user id.
     * sort: oldest (default), newest or updated.
     */
    @GetMapping
    public ResponseEntity<PageResponse<ApplicationSummary>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<ApplicationStatus> status,
            @RequestParam(required = false) OwnerType ownerType,
            @RequestParam(required = false) String county,
            @RequestParam(required = false) String assignee,
            @RequestParam(defaultValue = "oldest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reviewService.search(q, status, ownerType, county, assignee, sort, page, size));
    }

    /** Counters for the dashboard header. */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> stats() {
        return ResponseEntity.ok(reviewService.stats());
    }

    /** Staff an application can be assigned to. */
    @GetMapping("/reviewers")
    public ResponseEntity<List<StaffMember>> reviewers() {
        return ResponseEntity.ok(reviewService.reviewers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(reviewService.get(id));
    }

    /** Take the application: assigns it to me and starts the review if it was waiting. */
    @PostMapping("/{id}/claim")
    public ResponseEntity<ApplicationResponse> claim(@PathVariable String id) {
        return ResponseEntity.ok(reviewService.claim(id));
    }

    @PutMapping("/{id}/assign")
    public ResponseEntity<ApplicationResponse> assign(@PathVariable String id, @Valid @RequestBody Assign request) {
        return ResponseEntity.ok(reviewService.assign(id, request.getReviewerId()));
    }

    /** Move the application to PROCESSING, INFO_REQUESTED, VERIFIED or REJECTED. The applicant is emailed. */
    @PostMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> changeStatus(@PathVariable String id,
                                                            @Valid @RequestBody StatusChange request) {
        return ResponseEntity.ok(reviewService.changeStatus(id, request));
    }

    /** Verify or reject one uploaded document. The applicant is emailed. */
    @PostMapping("/{id}/documents/{documentId}/review")
    public ResponseEntity<ApplicationResponse> reviewDocument(@PathVariable String id,
                                                              @PathVariable String documentId,
                                                              @Valid @RequestBody DocumentReview request) {
        return ResponseEntity.ok(reviewService.reviewDocument(id, documentId, request));
    }

    /** Internal note, visible to staff only. */
    @PostMapping("/{id}/notes")
    public ResponseEntity<ApplicationResponse> addNote(@PathVariable String id, @Valid @RequestBody Note request) {
        return ResponseEntity.ok(reviewService.addNote(id, request.getText()));
    }

    /** View or download an uploaded file. Streams it from Drive; the file is never public. */
    @GetMapping("/{id}/documents/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id, @PathVariable String documentId,
                                           @RequestParam(defaultValue = "false") boolean download) {
        return HomeOwnerApplicationController.file(applicantService.downloadDocument(id, documentId), download);
    }
}