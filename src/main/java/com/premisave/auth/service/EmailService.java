package com.premisave.auth.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final RabbitTemplate rabbitTemplate;

    public EmailService(JavaMailSender mailSender, RabbitTemplate rabbitTemplate) {
        this.mailSender = mailSender;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void queueEmail(String to, String subject, String htmlContent) {
        try {
            // Send email directly for now (simplify)
            sendEmailDirectly(to, subject, htmlContent);
            System.out.println("DEBUG: Email sent directly to: " + to);
        } catch (Exception e) {
            System.err.println("ERROR: Failed to send email to " + to + ": " + e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendEmailDirectly(String to, String subject, String htmlContent) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
            System.out.println("SUCCESS: Email sent to: " + to);
        } catch (MessagingException e) {
            System.err.println("ERROR: Failed to send email to " + to + ": " + e.getMessage());
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }
}