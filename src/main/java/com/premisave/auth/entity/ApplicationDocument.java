package com.premisave.auth.entity;

import com.premisave.auth.enums.DocumentStatus;
import com.premisave.auth.enums.DocumentType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One file attached to an application. The file itself lives in Google Drive. */
@Data
public class ApplicationDocument {

    private String id = UUID.randomUUID().toString();
    private DocumentType type;

    private String originalFileName;
    private String storedFileName;
    private String mimeType;
    private long sizeBytes;
    private String sha256;

    private String driveFileId;
    private String driveViewLink;

    /** Optional details printed on the document, such as a licence number. */
    private String documentNumber;
    private LocalDate expiryDate;

    private DocumentStatus status = DocumentStatus.PENDING;
    private String reviewReason;
    private String reviewedById;
    private String reviewedByName;
    private LocalDateTime reviewedAt;

    private LocalDateTime uploadedAt = LocalDateTime.now();
}