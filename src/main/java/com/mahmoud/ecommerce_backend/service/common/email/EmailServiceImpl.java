package com.mahmoud.ecommerce_backend.service.common.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.mail.enabled}")
    private boolean mailEnabled;

    @Override
    public void send(String to, String subject, String body) {

        if (!mailEnabled) {
            log.info("[DEV-MAIL] to={} subject={} body={}", to, subject, body);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Failed to send email to " + to, ex);
        }
    }

    @Override
    public void sendEmailVerification(String to, String token) {

        String verificationLink = baseUrl + "/api/auth/verify-email?token=" + token;

        String subject = "Verify your email";
        String body = "Click the link to verify your account:\n" + verificationLink;

        send(to, subject, body);
    }
}
