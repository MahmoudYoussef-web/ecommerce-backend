package com.mahmoud.ecommerce_backend.service.inventory;

import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.StockReservationStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.repository.StockReservationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final StockReservationRepository reservationRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    private static final long TTL_SECONDS = 900; // 15 min


    @Transactional
    public StockReservation reserve(Long productId, int qty, Long orderId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BadRequestException("Product not found"));

        entityManager.lock(product, LockModeType.PESSIMISTIC_WRITE);

        if (product.getStockQuantity() < qty) {
            throw new BadRequestException("Insufficient stock");
        }

        return reservationRepository.save(
                StockReservation.builder()
                        .productId(productId)
                        .quantity(qty)
                        .status(StockReservationStatus.RESERVED)
                        .expiresAt(Instant.now().plusSeconds(TTL_SECONDS))
                        .orderId(orderId)
                        .build()
        );
    }


    @Transactional
    public void confirm(Long reservationId) {

        StockReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow();

        if (reservation.getStatus() == StockReservationStatus.CONFIRMED) return;

        Product product = productRepository.findById(reservation.getProductId())
                .orElseThrow();

        entityManager.lock(product, LockModeType.PESSIMISTIC_WRITE);

        if (product.getStockQuantity() < reservation.getQuantity()) {
            throw new BadRequestException("Stock changed, cannot confirm");
        }

        product.setStockQuantity(
                product.getStockQuantity() - reservation.getQuantity()
        );

        reservation.confirm();
    }


    @Transactional
    public void release(Long reservationId) {

        StockReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow();

        reservation.release();
    }


    @Transactional
    public void expire(Long reservationId) {

        StockReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow();

        reservation.expire();
    }
}