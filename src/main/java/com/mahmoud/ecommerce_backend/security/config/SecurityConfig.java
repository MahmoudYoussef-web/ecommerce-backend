package com.mahmoud.ecommerce_backend.security.config;

import com.mahmoud.ecommerce_backend.security.jwt.AuthTokenFilter;
import com.mahmoud.ecommerce_backend.security.jwt.JwtAuthEntryPoint;
import com.mahmoud.ecommerce_backend.security.user.ShopUserDetailsService;
import com.mahmoud.ecommerce_backend.tenant.TenantFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ShopUserDetailsService userDetailsService;
    private final JwtAuthEntryPoint authEntryPoint;
    private final AuthTokenFilter authTokenFilter;
    private final TenantFilter tenantFilter;


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean
    public DaoAuthenticationProvider authProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                  "status": 403,
                  "error": "FORBIDDEN",
                  "message": "Access denied",
                  "path": "%s"
                }
            """.formatted(request.getRequestURI()));
        };
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of("*")); // عدلها في production
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())

                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler())
                )

                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth


                        .requestMatchers(
                                "/api/auth/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()

                        .requestMatchers("/api/payments/webhook").permitAll()


                        .requestMatchers(HttpMethod.GET,
                                "/api/products/**",
                                "/api/categories/**"
                        ).permitAll()


                        .requestMatchers(HttpMethod.POST,
                                "/api/products/**",
                                "/api/categories/**"
                        ).hasAnyRole("ADMIN", "VENDOR")

                        .requestMatchers(HttpMethod.PUT,
                                "/api/products/**",
                                "/api/categories/**"
                        ).hasAnyRole("ADMIN", "VENDOR")

                        .requestMatchers(HttpMethod.DELETE,
                                "/api/products/**",
                                "/api/categories/**"
                        ).hasAnyRole("ADMIN", "VENDOR")


                        .requestMatchers("/api/payments/**").authenticated()

                        .anyRequest().authenticated()
                )

                .authenticationProvider(authProvider())


                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class)


                .addFilterAfter(tenantFilter, AuthTokenFilter.class);

        return http.build();
    }
}