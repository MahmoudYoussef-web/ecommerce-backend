package com.mahmoud.ecommerce_backend.entity;

import com.mahmoud.ecommerce_backend.enums.StockReservationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Where(clause = "is_deleted = false")
@Table(
        name = "stock_reservations",
        indexes = {
                @Index(name = "idx_reservation_product", columnList = "product_id"),
                @Index(name = "idx_reservation_order", columnList = "order_id"),
                @Index(name = "idx_reservation_status", columnList = "status"),
                @Index(name = "idx_reservation_expires", columnList = "expires_at")
        }
)
public class StockReservation extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "order_id")
    private Long orderId;

    @Version
    private Long version;

    // ================= DOMAIN =================

    public void confirm() {
        if (status != StockReservationStatus.RESERVED) {
            throw new IllegalStateException("Cannot confirm reservation");
        }
        this.status = StockReservationStatus.CONFIRMED;
    }

    public void release() {
        if (status == StockReservationStatus.CONFIRMED) return;
        this.status = StockReservationStatus.RELEASED;
    }

    public void expire() {
        if (status == StockReservationStatus.RESERVED) {
            this.status = StockReservationStatus.EXPIRED;
        }
    }
}