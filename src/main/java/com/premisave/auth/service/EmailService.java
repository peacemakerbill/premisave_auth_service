package com.premisave.auth.service;

import com.premisave.auth.config.RabbitMQConfig;
import com.premisave.auth.dto.EmailMessage;
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

    /**
     * Queue email for async processing with retry support
     */
    public void queueEmail(String to, String subject, String htmlContent) {
        try {
            EmailMessage emailMessage = new EmailMessage(to, subject, htmlContent);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EMAIL_QUEUE, emailMessage);
            System.out.println("Email queued successfully for: " + to);
        } catch (Exception e) {
            System.err.println("Failed to queue email to " + to + ". Trying direct send...");
            sendEmailDirectly(to, subject, htmlContent);
        }
    }

    /**
     * Direct email sending - used by consumer
     */
    public void sendEmailDirectly(String to, String subject, String htmlContent) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
            System.out.println("Email sent successfully to: " + to);
        } catch (MessagingException e) {
            System.err.println("Failed to send email to " + to + ": " + e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }
}