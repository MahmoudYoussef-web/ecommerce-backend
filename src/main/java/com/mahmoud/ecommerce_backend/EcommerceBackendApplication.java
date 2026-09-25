package com.mahmoud.ecommerce_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class EcommerceBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceBackendApplication.class, args);
    }

}