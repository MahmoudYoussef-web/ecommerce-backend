package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByName(String name);

    boolean existsByName(String name);
}