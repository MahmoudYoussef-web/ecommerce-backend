package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.StockReservation;
import com.mahmoud.ecommerce_backend.enums.StockReservationStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReservation r where r.id = :id")
    Optional<StockReservation> findByIdForUpdate(@Param("id") Long id);

    List<StockReservation> findByStatusAndExpiresAtBefore(
            StockReservationStatus status,
            Instant now
    );

    Optional<StockReservation> findByOrderId(Long orderId);
}