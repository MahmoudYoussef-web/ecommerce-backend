package com.mahmoud.ecommerce_backend.service.common.email;

public interface EmailService {

    void send(String to, String subject, String body);


    void sendEmailVerification(String to, String token);
}