package com.mahmoud.ecommerce_backend.service.common.email;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    @Override
    public void send(String to, String subject, String body) {
    }
}
