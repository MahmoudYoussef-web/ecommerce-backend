package com.mahmoud.ecommerce_backend.service.common.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    @Override
    public void send(String to, String subject, String body) {

    }

    @Override
    public void sendEmailVerification(String to, String token) {

        String verificationLink = "http://localhost:8080/api/auth/verify?token=" + token;

        String subject = "Verify your email";
        String body = "Click the link to verify your account:\n" + verificationLink;

        send(to, subject, body);
    }
}