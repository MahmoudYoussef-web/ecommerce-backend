package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.dto.auth.RegisterRequest;
import com.mahmoud.ecommerce_backend.entity.AddressSnapshot;
import com.mahmoud.ecommerce_backend.entity.Order;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.entity.UserRole;
import com.mahmoud.ecommerce_backend.enums.OrderStatus;
import com.mahmoud.ecommerce_backend.enums.RoleName;
import com.mahmoud.ecommerce_backend.repository.OrderRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import com.mahmoud.ecommerce_backend.repository.UserRoleRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityAndComplianceRegressionTest {

    static {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @org.junit.jupiter.api.Order(1)
    void publicCatalogIsAccessibleWithoutAuthentication() {
        ResponseEntity<String> resp = restTemplate.getForEntity(baseUrl() + "/api/products", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void corsPreflightFromAllowedOriginIsHonoured() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5173");
        headers.setAccessControlRequestMethod(HttpMethod.POST);

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/auth/login",
                HttpMethod.OPTIONS,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getHeaders().getAccessControlAllowOrigin())
                .contains("http://localhost:5173");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void corsPreflightFromForeignOriginIsNotHonoured() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://evil.example.com");
        headers.setAccessControlRequestMethod(HttpMethod.POST);

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/auth/login",
                HttpMethod.OPTIONS,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void refreshWithoutCookieIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5173");

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void crossSiteRefreshRequestIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://evil.example.com");

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl() + "/api/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody())
                .containsAnyOf("CSRF_REJECTED", "Invalid CORS request");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void refreshTokenRotatesThroughHttpOnlyCookie() {
        ResponseEntity<Map> login = login("user@gmail.com", "123456");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        String setCookie = login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("refresh_token cookie not set on login"));

        String cookieValue = setCookie.split(";")[0];

        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5173");
        headers.add(HttpHeaders.COOKIE, cookieValue);

        ResponseEntity<Map> refresh = restTemplate.exchange(
                baseUrl() + "/api/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
        );

        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) refresh.getBody().get("accessToken")).isNotBlank();
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    void registrationForcesCustomerRoleOnly() {
        String email = "rolecheck-" + UUID.randomUUID() + "@test.com";

        RegisterRequest request = RegisterRequest.builder()
                .firstName("Role")
                .lastName("Check")
                .email(email)
                .password("Password123!")
                .build();

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/register",
                request,
                Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        User user = userRepository.findByEmail(email).orElseThrow();
        List<UserRole> userRoles = userRoleRepository.findByUserIdWithRoles(user.getId());

        assertThat(userRoles)
                .extracting(ur -> ur.getRole().getName())
                .containsOnly(RoleName.ROLE_CUSTOMER);
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    void verifyEmailEndpointValidatesToken() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                baseUrl() + "/api/auth/verify-email?token=definitely-missing-token",
                Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    void actuatorHealthIsPublicAndReportsUp() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                baseUrl() + "/actuator/health",
                Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    void currencySnapshotPersistsOnOrder() {
        User user = userRepository.findByEmail("user@gmail.com").orElseThrow();

        Order order = Order.builder()
                .orderNumber("CUR-" + UUID.randomUUID())
                .user(user)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("120.00"))
                .shippingCost(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("120.00"))
                .shippingAddress(AddressSnapshot.builder()
                        .fullName("Test User")
                        .addressLine1("1 Test Street")
                        .city("Cairo")
                        .postalCode("11511")
                        .country("Egypt")
                        .build())
                .build();

        order.setCurrencySnapshot(
                new BigDecimal("5850.00"),
                new BigDecimal("48.75"),
                Instant.now()
        );

        Order saved = orderRepository.save(order);
        Order loaded = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getTotalAmountEgp()).isEqualByComparingTo("5850.00");
        assertThat(loaded.getExchangeRate()).isEqualByComparingTo("48.75");
        assertThat(loaded.getExchangeRateAt()).isNotNull();
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    void rateLimiterReturns429AfterBurst() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5173");
        headers.setContentType(MediaType.APPLICATION_JSON);

        int tooMany = 0;
        for (int i = 0; i < 20; i++) {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl() + "/api/auth/refresh",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    String.class
            );
            if (resp.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                tooMany++;
            }
        }

        assertThat(tooMany)
                .as("burst of refresh requests must be rate limited (429) after the burst capacity is exceeded")
                .isGreaterThan(5);
    }

    private ResponseEntity<Map> login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        return restTemplate.postForEntity(baseUrl() + "/api/auth/login", request, Map.class);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
