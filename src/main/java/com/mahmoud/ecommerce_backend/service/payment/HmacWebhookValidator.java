package com.mahmoud.ecommerce_backend.service.payment;

import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Component
public class HmacWebhookValidator implements WebhookValidator {

    private static final long MAX_TIMESTAMP_DIFF_SECONDS = 300;

    @Value("${payment.webhook.secret}")
    private String webhookSecret;

    @Override
    public void validate(String payload, String signature, String timestampHeader) {

        long timestamp = parseTimestamp(timestampHeader);
        validateTimestamp(timestamp);
        validateSignature(payload, signature, timestampHeader);
    }

    private long parseTimestamp(String timestampHeader) {
        try {
            return Long.parseLong(timestampHeader);
        } catch (Exception e) {
            throw new ForbiddenException("Invalid timestamp header");
        }
    }

    private void validateTimestamp(long timestamp) {
        long now = Instant.now().getEpochSecond();
        long diff = Math.abs(now - timestamp);

        if (diff > MAX_TIMESTAMP_DIFF_SECONDS) {
            throw new ForbiddenException("Webhook request expired");
        }
    }

    private void validateSignature(String payload, String signature, String timestamp) {
        try {
            String signedPayload = timestamp + "." + payload;

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            byte[] rawHmac = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = bytesToHex(rawHmac);

            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                throw new ForbiddenException("Invalid webhook signature");
            }

        } catch (Exception e) {
            throw new ForbiddenException("Invalid webhook signature");
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String s = Integer.toHexString(0xff & b);
            if (s.length() == 1) hex.append('0');
            hex.append(s);
        }
        return hex.toString();
    }
}