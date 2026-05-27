package com.premisave.auth.service;

import com.premisave.auth.config.RabbitMQConfig;
import com.premisave.auth.dto.EmailMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EmailConsumer {

    private final EmailService emailService;

    public EmailConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Consumes emails from queue with retry mechanism
     */
    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void receiveAndSendEmail(EmailMessage emailMessage) {
        int maxRetries = 3;
        int attempt = 0;
        long delay = 1000; // 1 second initial delay

        while (attempt < maxRetries) {
            try {
                System.out.println("Processing email for: " + emailMessage.getTo() + " (Attempt " + (attempt + 1) + ")");
                
                emailService.sendEmailDirectly(
                    emailMessage.getTo(),
                    emailMessage.getSubject(),
                    emailMessage.getContent()
                );
                
                System.out.println("Email sent successfully to: " + emailMessage.getTo());
                return; // Success - exit method
                
            } catch (Exception e) {
                attempt++;
                System.err.println("Email sending failed (Attempt " + attempt + "/" + maxRetries + "): " + e.getMessage());
                
                if (attempt >= maxRetries) {
                    System.err.println("Max retries reached. Email to " + emailMessage.getTo() + " will not be retried further.");
                    // TODO: You can send to Dead Letter Queue here in future
                    break;
                }
                
                // Exponential backoff: 1s, 2s, 4s...
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                delay *= 2; // Double the delay for next retry
            }
        }
    }
}