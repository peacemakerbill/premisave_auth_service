package com.premisave.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final ResourceLoader resourceLoader;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${email.support:thepeacemakerske@gmail.com}")
    private String supportEmail;

    public EmailService(JavaMailSender mailSender, ResourceLoader resourceLoader) {
        this.mailSender = mailSender;
        this.resourceLoader = resourceLoader;
        System.out.println("EmailService initialized - Direct Async Mode");
    }

    @Async
    public void sendVerificationEmail(String to, String token) {
        try {
            String activationLink = frontendUrl + "/verify/" + token;
            
            Map<String, String> data = new HashMap<>();
            data.put("activationLink", activationLink);
            data.put("supportEmail", supportEmail);
            data.put("currentYear", String.valueOf(Year.now().getValue()));

            String htmlContent = processTemplate("templates/activation-email.html", data);
            
            sendEmail(to, "Activate Your Premisave Account", htmlContent);
            System.out.println("Verification email sent successfully to: " + to);

        } catch (Exception e) {
            System.err.println("Failed to send verification email to " + to);
            e.printStackTrace();
        }
    }

    @Async
    public void sendResetPasswordEmail(String to, String token) {
        try {
            String resetLink = frontendUrl + "/reset-password?token=" + token;
            
            Map<String, String> data = new HashMap<>();
            data.put("resetLink", resetLink);
            data.put("supportEmail", supportEmail);
            data.put("currentYear", String.valueOf(Year.now().getValue()));

            String htmlContent = processTemplate("templates/reset-password-email.html", data);
            
            sendEmail(to, "Reset Your Premisave Password", htmlContent);
            System.out.println("Password reset email sent successfully to: " + to);

        } catch (Exception e) {
            System.err.println("Failed to send reset password email to " + to);
            e.printStackTrace();
        }
    }

    @Async
    public void sendEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            System.err.println("Email sending failed to " + to + ": " + e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    private String processTemplate(String templatePath, Map<String, String> data) {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + templatePath);
            String template = FileCopyUtils.copyToString(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );

            for (Map.Entry<String, String> entry : data.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                template = template.replace(placeholder, entry.getValue());
            }

            return template;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load email template: " + templatePath, e);
        }
    }
}