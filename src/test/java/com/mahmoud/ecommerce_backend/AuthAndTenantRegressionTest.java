package com.mahmoud.ecommerce_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahmoud.ecommerce_backend.dto.auth.LoginRequest;
import com.mahmoud.ecommerce_backend.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.tomcat.threads.max=1"
)
@Import(AuthAndTenantRegressionTest.TenantProbeConfig.class)
class AuthAndTenantRegressionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetProbe() {
        TenantProbeFilter.records.clear();
    }

    @Test
    void authenticatedRequestThroughFilterSucceeds() {
        String token = loginAndGetToken();

        ResponseEntity<String> me = restTemplate.exchange(
                baseUrl() + "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(bearer(token)),
                String.class
        );

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unauthenticatedRequestReturnsSerializableErrorEnvelope() throws Exception {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                baseUrl() + "/api/users/me",
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Map<String, Object> body = objectMapper.readValue(resp.getBody(), Map.class);

        assertThat(body.get("timestamp")).isNotNull();
        assertThat(body.get("status")).isEqualTo(401);
        assertThat(body.get("errorCode")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void tenantContextDoesNotLeakAcrossReusedTomcatThread() {
        String token = loginAndGetToken();
        TenantProbeFilter.records.clear();

        ResponseEntity<String> first = restTemplate.exchange(
                baseUrl() + "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(bearer(token)),
                String.class
        );
        ResponseEntity<String> second = restTemplate.exchange(
                baseUrl() + "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(bearer(token)),
                String.class
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(TenantProbeFilter.records).hasSize(2);

        String threadFirst = (String) TenantProbeFilter.records.get(0)[0];
        String threadSecond = (String) TenantProbeFilter.records.get(1)[0];
        assertThat(threadFirst)
                .as("thread reuse required: server.tomcat.threads.max=1 must pin both requests to the same worker")
                .isEqualTo(threadSecond);

        assertThat(TenantProbeFilter.records.get(1)[1])
                .as("tenant context must not leak from the previous request into this one")
                .isNull();
    }

    private String loginAndGetToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@gmail.com");
        request.setPassword("123456");

        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                request,
                Map.class
        );

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        String token = (String) login.getBody().get("accessToken");
        assertThat(token).isNotBlank();
        return token;
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @TestConfiguration
    static class TenantProbeConfig {

        @Bean
        FilterRegistrationBean<TenantProbeFilter> tenantProbeFilter() {
            FilterRegistrationBean<TenantProbeFilter> registration =
                    new FilterRegistrationBean<>(new TenantProbeFilter());
            registration.addUrlPatterns("/*");
            registration.setOrder(Integer.MIN_VALUE);
            return registration;
        }
    }

    static class TenantProbeFilter extends OncePerRequestFilter {

        static final List<Object[]> records = new CopyOnWriteArrayList<>();

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain)
                throws ServletException, IOException {

            records.add(new Object[]{
                    Thread.currentThread().getName(),
                    TenantContext.getOrNull()
            });

            filterChain.doFilter(request, response);
        }
    }
}