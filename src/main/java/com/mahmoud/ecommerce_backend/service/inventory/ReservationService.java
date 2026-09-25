package com.mahmoud.ecommerce_backend.service.inventory;

import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.StockReservationStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final StockReservationRepository reservationRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final long TTL_SECONDS = 900; // 15 min


    @Transactional
    public StockReservation reserve(Long productId, int qty, Long orderId) {

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BadRequestException("Product not found"));

        long reserved = reservationRepository.sumQuantityByProductAndStatus(
                productId,
                StockReservationStatus.RESERVED
        );

        long available = product.getStockQuantity() - reserved;

        if (qty > available) {
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


    /**
     * Confirms all reservations for the order, decrementing product stock.
     * Deliberately NOT @Transactional: it joins the caller's transaction so a
     * stock conflict surfaces as a catchable exception WITHOUT marking the
     * surrounding payment/webhook transaction rollback-only. Partial confirms
     * are acceptable — releaseForOrder() no-ops on CONFIRMED reservations, so
     * allocated stock stays allocated for a flagged order.
     */
    public void confirmForOrder(Long orderId) {

        for (StockReservation reservation : reservationRepository.findAllByOrderId(orderId)) {
            confirm(reservation.getId());
        }
    }


    @Transactional
    public void releaseForOrder(Long orderId) {

        for (StockReservation reservation : reservationRepository.findAllByOrderId(orderId)) {
            release(reservation.getId());
        }
    }


    private void confirm(Long reservationId) {

        StockReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow();

        // Idempotent: only RESERVED reservations consume stock. CONFIRMED are
        // already allocated; RELEASED/EXPIRED belong to earlier attempts of a
        // retried payment and must be skipped, not fatal.
        if (reservation.getStatus() != StockReservationStatus.RESERVED) return;

        Product product = productRepository.findByIdForUpdate(reservation.getProductId())
                .orElseThrow();

        if (product.getStockQuantity() < reservation.getQuantity()) {
            throw new BadRequestException("Stock changed, cannot confirm");
        }

        product.setStockQuantity(
                product.getStockQuantity() - reservation.getQuantity()
        );

        reservation.confirm();

        // Authoritative stock change → product caches must be evicted once
        // this transaction commits (Phase 6 stale-stock fix).
        eventPublisher.publishEvent(
                new com.mahmoud.ecommerce_backend.event.inventory.ProductStockChangedEvent(
                        product.getId()));
    }


    private void release(Long reservationId) {

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