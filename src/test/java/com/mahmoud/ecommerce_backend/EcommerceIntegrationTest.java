package com.mahmoud.ecommerce_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.dto.order.CreateOrderRequest;
import com.mahmoud.ecommerce_backend.entity.Payment;
import com.mahmoud.ecommerce_backend.enums.PaymentStatus;
import com.mahmoud.ecommerce_backend.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EcommerceIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl() {
        return "http://localhost:" + port;
    }


    private String loginAndGetToken() throws Exception {

        LoginRequest request = new LoginRequest();
        request.setEmail("user@gmail.com");
        request.setPassword("123456");

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body =
                objectMapper.readValue(response.getBody(), Map.class);

        return (String) ((Map<?, ?>) body.get("data")).get("accessToken");
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }


    @Test
    void fullFlow_order_payment_inventory() throws Exception {

        String token = loginAndGetToken();


        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setAddressId(1L);
        orderRequest.setCustomerNotes("test order");

        HttpEntity<CreateOrderRequest> orderEntity =
                new HttpEntity<>(orderRequest, authHeaders(token));

        ResponseEntity<String> orderResponse =
                restTemplate.postForEntity(
                        baseUrl() + "/api/orders",
                        orderEntity,
                        String.class
                );

        assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> orderBody =
                objectMapper.readValue(orderResponse.getBody(), Map.class);

        Integer orderId = (Integer) ((Map<?, ?>) orderBody.get("data")).get("id");

        assertThat(orderId).isNotNull();


        HttpEntity<Void> paymentEntity =
                new HttpEntity<>(authHeaders(token));

        ResponseEntity<String> paymentResponse =
                restTemplate.postForEntity(
                        baseUrl() + "/api/payments?orderId=" + orderId + "&method=CARD",
                        paymentEntity,
                        String.class
                );

        assertThat(paymentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> paymentBody =
                objectMapper.readValue(paymentResponse.getBody(), Map.class);

        Integer paymentId = (Integer) ((Map<?, ?>) paymentBody.get("data")).get("id");


        String webhookUrl = baseUrl() + "/api/payments/webhook";

        Map<String, Object> webhookPayload = Map.of(
                "eventId", "evt_test_123",
                "paymentId", paymentId,
                "status", "COMPLETED",
                "reference", "stripe_test_ref"
        );

        HttpEntity<Map<String, Object>> webhookEntity =
                new HttpEntity<>(webhookPayload, new HttpHeaders());

        ResponseEntity<String> webhookResponse =
                restTemplate.postForEntity(
                        webhookUrl,
                        webhookEntity,
                        String.class
                );

        assertThat(webhookResponse.getStatusCode()).isEqualTo(HttpStatus.OK);


        Payment payment = paymentRepository.findById(paymentId.longValue())
                .orElseThrow();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getGatewayReference()).isEqualTo("stripe_test_ref");


        assertThat(payment.getOrder().getStatus().name()).isEqualTo("PAID");


        assertThat(payment.getAmount()).isGreaterThan(BigDecimal.ZERO);
    }
}