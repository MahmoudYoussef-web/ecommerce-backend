package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    List<RefreshToken> findByUserIdAndRevokedFalseAndExpiresAtAfter(Long userId, Instant now);

    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);

    Optional<RefreshToken> findByIdAndRevokedFalse(Long id);

    List<RefreshToken> findByRevokedFalseAndExpiresAtAfter(Instant now);

    List<RefreshToken> findByRevokedFalse();
}