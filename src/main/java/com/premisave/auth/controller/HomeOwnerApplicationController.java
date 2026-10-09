package com.premisave.auth.controller;

import com.premisave.auth.dto.application.ApplicationDtos.ApplicationResponse;
import com.premisave.auth.dto.application.ApplicationDtos.ApplicationSummary;
import com.premisave.auth.dto.application.ApplicationDtos.Eligibility;
import com.premisave.auth.dto.application.ApplicationDtos.MetaResponse;
import com.premisave.auth.dto.application.ApplicationRequest;
import com.premisave.auth.dto.application.ApplicationRequests.Withdraw;
import com.premisave.auth.enums.DocumentType;
import com.premisave.auth.service.application.ApplicationMetaService;
import com.premisave.auth.service.application.HomeOwnerApplicationService;
import com.premisave.auth.service.application.HomeOwnerApplicationService.DownloadedDocument;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Applicant side of the Home Owner application. Any signed-in user may call
 * these; the service decides whether the account is allowed to apply.
 */
@RestController
@RequestMapping("/home-owner/applications")
public class HomeOwnerApplicationController {

    private final HomeOwnerApplicationService service;
    private final ApplicationMetaService metaService;

    public HomeOwnerApplicationController(HomeOwnerApplicationService service, ApplicationMetaService metaService) {
        this.service = service;
        this.metaService = metaService;
    }

    /** Form options, document checklist and upload limits. */
    @GetMapping("/meta")
    public ResponseEntity<MetaResponse> meta() {
        return ResponseEntity.ok(metaService.meta());
    }

    /** Can this account apply right now, and if not, why. */
    @GetMapping("/eligibility")
    public ResponseEntity<Eligibility> eligibility() {
        return ResponseEntity.ok(service.eligibility());
    }

    /** Start an application. The body is optional; any fields sent are saved straight away. */
    @PostMapping
    public ResponseEntity<ApplicationResponse> start(@Valid @RequestBody(required = false) ApplicationRequest request) {
        return ResponseEntity.status(201).body(service.start(request));
    }

    /** My open application, or my latest one if none is open. 404 if I never applied. */
    @GetMapping("/mine")
    public ResponseEntity<ApplicationResponse> mine() {
        return ResponseEntity.ok(service.getMine());
    }

    /** All of my applications, newest first. */
    @GetMapping
    public ResponseEntity<List<ApplicationSummary>> list() {
        return ResponseEntity.ok(service.listMine());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(service.get(id));
    }

    /** Save changes. Only the fields sent are changed, so the form can autosave section by section. */
    @PatchMapping("/{id}")
    public ResponseEntity<ApplicationResponse> update(@PathVariable String id,
                                                      @Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    /** multipart/form-data: type, file, optional documentNumber, optional expiryDate (yyyy-MM-dd). */
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApplicationResponse> uploadDocument(
            @PathVariable String id,
            @RequestParam(required = false) DocumentType type,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String documentNumber,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate) {
        return ResponseEntity.status(201).body(service.uploadDocument(id, type, file, documentNumber, expiryDate));
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    public ResponseEntity<ApplicationResponse> deleteDocument(@PathVariable String id, @PathVariable String documentId) {
        return ResponseEntity.ok(service.deleteDocument(id, documentId));
    }

    /** Streams the file. Add ?download=true to force a download instead of showing it in the browser. */
    @GetMapping("/{id}/documents/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id, @PathVariable String documentId,
                                           @RequestParam(defaultValue = "false") boolean download) {
        return file(service.downloadDocument(id, documentId), download);
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApplicationResponse> submit(@PathVariable String id) {
        return ResponseEntity.ok(service.submit(id));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ApplicationResponse> withdraw(@PathVariable String id,
                                                        @Valid @RequestBody(required = false) Withdraw request) {
        return ResponseEntity.ok(service.withdraw(id, request == null ? null : request.getReason()));
    }

    /** Delete a draft that was never submitted. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteDraft(@PathVariable String id) {
        service.deleteDraft(id);
        return ResponseEntity.ok(Map.of("message", "Draft deleted."));
    }

    static ResponseEntity<byte[]> file(DownloadedDocument doc, boolean download) {
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(doc.fileName() == null ? "document" : doc.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(doc.mimeType()))
                .contentLength(doc.content().length)
                .body(doc.content());
    }
}