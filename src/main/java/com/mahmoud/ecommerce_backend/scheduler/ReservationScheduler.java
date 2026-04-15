package com.mahmoud.ecommerce_backend.scheduler;

import com.mahmoud.ecommerce_backend.entity.StockReservation;
import com.mahmoud.ecommerce_backend.enums.StockReservationStatus;
import com.mahmoud.ecommerce_backend.repository.StockReservationRepository;
import com.mahmoud.ecommerce_backend.service.inventory.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReservationScheduler {

    private final StockReservationRepository repository;
    private final ReservationService reservationService;

    @Scheduled(fixedRate = 60000)
    public void expireReservations() {

        List<StockReservation> expired =
                repository.findByStatusAndExpiresAtBefore(
                        StockReservationStatus.RESERVED,
                        Instant.now()
                );

        expired.forEach(r -> reservationService.expire(r.getId()));
    }
}