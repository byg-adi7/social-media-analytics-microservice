package com.audienceinsights.audience_insights_auth_service.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String appBaseUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@example.com}") String mailFrom,
            @Value("${app.base-url:http://localhost:8080}") String appBaseUrl) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Sends the real verification email. A send failure is logged (with the
     * link itself, so it can still be used manually) rather than thrown -
     * registration must succeed even if the mail provider has a transient
     * outage; the user can request the resend endpoint once mail recovers.
     */
    public void sendVerificationEmail(String to, String username, String token) {
        String verificationLink = appBaseUrl + "/api/auth/verify-email?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("Verify your Audience Insights account");
        message.setText(
                "Hi " + username + ",\n\n" +
                        "Welcome to Audience Insights! Please verify your email address by opening the link below:\n\n" +
                        verificationLink + "\n\n" +
                        "This link expires in 24 hours. If you didn't create this account, you can ignore this email.\n"
        );

        try {
            mailSender.send(message);
            log.info("Sent verification email to {}", to);
        } catch (MailException ex) {
            log.error("Failed to send verification email to {}: {}. Verification link: {}",
                    to, ex.getMessage(), verificationLink);
        }
    }
}
