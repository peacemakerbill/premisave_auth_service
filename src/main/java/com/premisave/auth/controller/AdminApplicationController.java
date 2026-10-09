package com.premisave.auth.controller;

import com.premisave.auth.dto.application.ApplicationDtos.PromoteResponse;
import com.premisave.auth.dto.application.ApplicationRequests.Promote;
import com.premisave.auth.service.application.ApplicationReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Administrator-only actions on applications. */
@RestController
@RequestMapping("/admin/applications")
public class AdminApplicationController {

    private final ApplicationReviewService reviewService;

    public AdminApplicationController(ApplicationReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * The "Make Home Owner" button. Changes the applicant's role from CLIENT to HOME_OWNER,
     * marks the application ACCEPTED and emails the applicant. The application must be VERIFIED.
     * The body is optional.
     */
    @PostMapping("/{id}/promote")
    public ResponseEntity<PromoteResponse> promote(@PathVariable String id,
                                                   @Valid @RequestBody(required = false) Promote request) {
        return ResponseEntity.ok(reviewService.promote(id, request));
    }
}