package com.premisave.auth.entity;

import com.premisave.auth.enums.ApplicationEventType;
import com.premisave.auth.enums.ApplicationStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** One line of the application timeline. */
@Data
public class ApplicationEvent {

    private ApplicationEventType type;
    private ApplicationStatus fromStatus;
    private ApplicationStatus toStatus;

    private String actorId;
    private String actorName;
    private String actorRole;
    private boolean byApplicant;

    /** Shown to the applicant. Never put internal remarks here. */
    private String message;

    private LocalDateTime at = LocalDateTime.now();
}