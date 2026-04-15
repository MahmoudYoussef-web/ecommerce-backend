package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.ChartOfAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Long> {

    Optional<ChartOfAccount> findByCode(String code);
}