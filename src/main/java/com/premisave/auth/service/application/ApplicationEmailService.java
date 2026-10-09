package com.premisave.auth.service.application;

import com.premisave.auth.entity.ApplicationDocument;
import com.premisave.auth.entity.HomeOwnerApplication;
import com.premisave.auth.enums.ApplicationStatus;
import com.premisave.auth.enums.DocumentStatus;
import com.premisave.auth.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emails the applicant whenever something about their application changes.
 * Every method runs in the background and never throws: a mail problem must
 * not undo a review decision.
 */
@Slf4j
@Service
public class ApplicationEmailService {

    private static final String TEMPLATE = "templates/application-update-email.html";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");
    private static final String[] STEP_LABELS = {"Submitted", "In review", "Verified", "Accepted"};

    private final EmailService emailService;
    private final ResourceLoader resourceLoader;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${email.support:thepeacemakerske@gmail.com}")
    private String supportEmail;

    @Value("${home-owner-application.frontend-path:/home-owner/application}")
    private String frontendPath;

    public ApplicationEmailService(EmailService emailService, ResourceLoader resourceLoader) {
        this.emailService = emailService;
        this.resourceLoader = resourceLoader;
    }

    @Async
    public void sendSubmitted(HomeOwnerApplication app, boolean resubmission) {
        String headline = resubmission ? "We received your updates" : "We received your application";
        String intro = resubmission
                ? "Thank you for the changes. Your application is back in the queue and a reviewer will take another look."
                : "Thank you for applying to become a Home Owner. A reviewer will check your details and documents, and we will email you at every step.";
        send(app, app.getStatus(), resubmission ? "Updates received for " : "Application received: ",
                headline, intro, null, "Track your application");
    }

    @Async
    public void sendStatusChanged(HomeOwnerApplication app, ApplicationStatus from, String message) {
        ApplicationStatus to = app.getStatus();
        String headline;
        String intro;
        String cta = "Open your application";
        switch (to) {
            case PROCESSING -> {
                headline = "A reviewer has started on your application";
                intro = "Your application is now being reviewed. You do not need to do anything right now.";
            }
            case INFO_REQUESTED -> {
                headline = "We need something from you";
                intro = "The reviewer could not finish yet. Please read the message below, make the changes, and resubmit your application.";
                cta = "Fix and resubmit";
            }
            case VERIFIED -> {
                headline = "Your details and documents are verified";
                intro = "Good news. Everything you provided checks out. An administrator will confirm your Home Owner access next, and we will email you as soon as it is done.";
            }
            case REJECTED -> {
                headline = "Your application was not approved";
                intro = "We are sorry, we could not approve this application. The reason is below. You are welcome to apply again once it is addressed.";
                cta = "View details";
            }
            case WITHDRAWN -> {
                headline = "Your application was withdrawn";
                intro = "This application has been withdrawn. You can start a new one whenever you are ready.";
                cta = "Start a new application";
            }
            default -> {
                headline = "Your application was updated";
                intro = "The status of your application changed from " + from.getLabel() + " to " + to.getLabel() + ".";
            }
        }
        send(app, to, "Update on ", headline, intro, message, cta);
    }

    @Async
    public void sendDocumentReviewed(HomeOwnerApplication app, ApplicationDocument document) {
        boolean verified = document.getStatus() == DocumentStatus.VERIFIED;
        String label = document.getType() == null ? "A document" : document.getType().getLabel();
        String headline = verified ? label + " was verified" : label + " needs replacing";
        String intro = verified
                ? "A reviewer checked this document and it is fine. There is nothing you need to do."
                : "A reviewer could not accept this document. Please upload a clear replacement.";
        String message = verified ? null : document.getReviewReason();
        send(app, app.getStatus(), "Document update for ", headline, intro, message,
                verified ? "Open your application" : "Upload a replacement");
    }

    @Async
    public void sendPromoted(HomeOwnerApplication app, String message) {
        send(app, ApplicationStatus.ACCEPTED, "You are now a Home Owner: ",
                "Welcome, you are now a Home Owner",
                "Your application was approved and your account now has Home Owner access. "
                        + "Sign out and sign in again so your new access takes effect everywhere.",
                message, "Go to Premisave");
    }

    // ------------------------------------------------------------------

    private void send(HomeOwnerApplication app, ApplicationStatus shownStatus, String subjectPrefix, String headline,
                      String intro, String message, String ctaLabel) {
        if (app.getApplicantEmail() == null || app.getApplicantEmail().isBlank()) {
            return;
        }
        try {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("title", HtmlUtils.htmlEscape(headline));
            vars.put("applicantName", HtmlUtils.htmlEscape(firstName(app.getApplicantName())));
            vars.put("headline", HtmlUtils.htmlEscape(headline));
            vars.put("statusLabel", HtmlUtils.htmlEscape(shownStatus.getLabel()));
            vars.put("statusColor", shownStatus.getColor());
            vars.put("intro", HtmlUtils.htmlEscape(intro));
            vars.put("messageBlock", messageBlock(message));
            vars.put("stepsHtml", stepsHtml(shownStatus));
            vars.put("detailsRows", detailsRows(app, shownStatus));
            vars.put("ctaLink", frontendUrl + frontendPath);
            vars.put("ctaLabel", HtmlUtils.htmlEscape(ctaLabel));
            vars.put("supportEmail", HtmlUtils.htmlEscape(supportEmail));
            vars.put("currentYear", String.valueOf(Year.now().getValue()));

            String subject = subjectPrefix + app.getApplicationNumber();
            emailService.sendEmail(app.getApplicantEmail(), subject, render(vars));
            log.info("Application email '{}' queued for {}", headline, app.getApplicationNumber());
        } catch (Exception e) {
            log.error("Could not send application email for {}: {}", app.getApplicationNumber(), e.getMessage());
        }
    }

    private String messageBlock(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String safe = HtmlUtils.htmlEscape(message.trim()).replace("\r\n", "<br>").replace("\n", "<br>");
        return "<div class=\"message\" style=\"margin:20px 0;padding:16px 18px;background:#f9fafb;border-radius:8px;"
                + "border-left:4px solid #2a8f5e;font-size:15px;line-height:1.6;color:#374151;\">"
                + "<strong>Message from the reviewer</strong><br>" + safe + "</div>";
    }

    /** A four step tracker. Hidden for withdrawn and rejected applications. */
    private String stepsHtml(ApplicationStatus status) {
        int reached = switch (status) {
            case SUBMITTED -> 1;
            case PROCESSING, INFO_REQUESTED -> 2;
            case VERIFIED -> 3;
            case ACCEPTED -> 4;
            default -> 0;
        };
        if (reached == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<table class=\"steps\" role=\"presentation\"><tr>");
        for (int i = 0; i < STEP_LABELS.length; i++) {
            boolean done = i < reached;
            String color = done ? "#2a8f5e" : "#d1d5db";
            sb.append("<td align=\"center\" style=\"padding:0 4px;font-size:12px;color:")
                    .append(done ? "#111827" : "#9ca3af").append(";\">")
                    .append("<div style=\"width:28px;height:28px;line-height:28px;border-radius:50%;background:")
                    .append(color).append(";color:#fff;font-weight:bold;margin:0 auto 6px;\">")
                    .append(i + 1).append("</div>")
                    .append(STEP_LABELS[i]).append("</td>");
        }
        sb.append("</tr></table>");
        return sb.toString();
    }

    private String detailsRows(HomeOwnerApplication app, ApplicationStatus shownStatus) {
        StringBuilder sb = new StringBuilder();
        row(sb, "Application number", app.getApplicationNumber());
        row(sb, "Status", shownStatus.getLabel());
        if (app.getSubmittedAt() != null) {
            row(sb, "Submitted", app.getSubmittedAt().format(WHEN));
        }
        row(sb, "Last update", LocalDateTime.now().format(WHEN));
        return sb.toString();
    }

    private void row(StringBuilder sb, String key, String value) {
        sb.append("<tr><td class=\"k\" style=\"color:#6b7280;\">").append(HtmlUtils.htmlEscape(key))
                .append("</td><td><strong>").append(HtmlUtils.htmlEscape(value == null ? "" : value))
                .append("</strong></td></tr>");
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "there";
        }
        return fullName.trim().split("\\s+")[0];
    }

    private String render(Map<String, String> vars) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:" + TEMPLATE);
        String template = FileCopyUtils.copyToString(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }
}