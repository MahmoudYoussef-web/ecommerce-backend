package com.mahmoud.ecommerce_backend.service.payment;

import com.mahmoud.ecommerce_backend.entity.Payment;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.repository.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StripePaymentProvider implements PaymentProvider {

    private final PaymentRepository paymentRepository;

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook.secret}")
    private String stripeWebhookSecret;

    @Value("${app.base-url}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    @Override
    public String createCheckoutSession(Long paymentId) {

        if (paymentId == null) {
            throw new BadRequestException("PaymentId must not be null");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getOrder() == null) {
            throw new BadRequestException("Payment not linked to order");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Invalid payment state");
        }

        if (payment.getOrder().getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Order is not payable");
        }

        try {

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(baseUrl + "/success?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(baseUrl + "/cancel")
                    .putMetadata("paymentId", String.valueOf(payment.getId()))
                    .putMetadata("orderId", String.valueOf(payment.getOrder().getId()))
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(payment.getCurrency().toLowerCase())
                                                    .setUnitAmount(payment.getAmount()
                                                            .multiply(java.math.BigDecimal.valueOf(100))
                                                            .longValueExact())
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("Order #" + payment.getOrder().getId())
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);

            return session.getUrl();

        } catch (Exception e) {
            throw new IllegalStateException("Stripe session creation failed");
        }
    }

    @Override
    public void handleWebhook(String payload) {
        throw new UnsupportedOperationException("Use handleWebhook(payload, signature)");
    }

    public Event handleWebhook(String payload, String signature) {

        try {
            return Webhook.constructEvent(payload, signature, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            throw new ForbiddenException("Invalid Stripe signature");
        }
    }
}