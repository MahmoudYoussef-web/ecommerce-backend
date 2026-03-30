package com.mahmoud.ecommerce_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahmoud.ecommerce_backend.common.ApiResponse;
import com.mahmoud.ecommerce_backend.dto.payment.CreatePaymentRequest;
import com.mahmoud.ecommerce_backend.dto.payment.PaymentResponse;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import com.mahmoud.ecommerce_backend.service.payment.PaymentService;
import com.mahmoud.ecommerce_backend.service.payment.StripePaymentProvider;
import com.mahmoud.ecommerce_backend.service.payment.WebhookValidator;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment management APIs")
public class PaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final WebhookValidator webhookValidator;
    private final StripePaymentProvider stripePaymentProvider;

    @Operation(summary = "Create payment for order")
    @PostMapping
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        return ApiResponse.success(
                paymentService.createPayment(request.getOrderId(), request.getMethod()),
                "Payment created successfully"
        );
    }

    @Operation(summary = "Create Stripe checkout session")
    @PostMapping("/checkout/{paymentId}")
    public ApiResponse<String> createCheckout(@PathVariable Long paymentId) {
        String url = paymentService.createCheckoutSession(paymentId);
        return ApiResponse.success(url, "Checkout session created");
    }

    @Operation(summary = "Webhook endpoint for payment provider")
    @PostMapping("/webhook")
    public ApiResponse<Void> webhook(HttpServletRequest request,
                                     @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
                                     @RequestHeader(value = "X-Signature", required = false) String signature,
                                     @RequestHeader(value = "X-Timestamp", required = false) String timestampHeader) throws Exception {

        String rawBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);

        if (stripeSignature != null && (signature != null || timestampHeader != null)) {
            throw new ForbiddenException("Ambiguous webhook source");
        }

        if (stripeSignature != null) {

            Event event = stripePaymentProvider.handleWebhook(rawBody, stripeSignature);

            if ("checkout.session.completed".equals(event.getType())) {

                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new ForbiddenException("Invalid session data"));

                String paymentIdStr = session.getMetadata().get("paymentId");
                String orderIdStr = session.getMetadata().get("orderId");

                if (paymentIdStr == null || orderIdStr == null) {
                    throw new ForbiddenException("Missing metadata");
                }

                Long paymentId = Long.valueOf(paymentIdStr);

                BigDecimal amountFromStripe = BigDecimal.valueOf(session.getAmountTotal())
                        .divide(BigDecimal.valueOf(100));

                String currencyFromStripe = session.getCurrency();

                paymentService.processStripeWebhook(
                        event.getId(),
                        paymentId,
                        PaymentStatus.COMPLETED,
                        session.getId(),
                        amountFromStripe,
                        currencyFromStripe
                );
            }

            return ApiResponse.success(null, "Stripe webhook processed");
        }

        if (signature == null || timestampHeader == null) {
            throw new ForbiddenException("Invalid webhook headers");
        }

        webhookValidator.validate(rawBody, signature, timestampHeader);

        JsonNode json = objectMapper.readTree(rawBody);

        if (!json.hasNonNull("eventId") || !json.hasNonNull("paymentId") || !json.hasNonNull("status")) {
            throw new ForbiddenException("Invalid webhook payload");
        }

        String eventId = json.get("eventId").asText();
        Long paymentId = json.get("paymentId").asLong();
        PaymentStatus status = PaymentStatus.valueOf(json.get("status").asText());
        String reference = json.has("reference") && !json.get("reference").isNull()
                ? json.get("reference").asText()
                : null;

        paymentService.processWebhook(eventId, paymentId, status, reference);

        return ApiResponse.success(null, "Webhook processed");
    }
}