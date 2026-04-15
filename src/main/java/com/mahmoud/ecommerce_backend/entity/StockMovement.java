package com.mahmoud.ecommerce_backend.entity;

import com.mahmoud.ecommerce_backend.enums.StockMovementType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "stock_movements",
        indexes = {
                @Index(name = "idx_stock_product", columnList = "product_id"),
                @Index(name = "idx_stock_variant", columnList = "variant_id"),
                @Index(name = "idx_stock_type", columnList = "movement_type"),
                @Index(name = "idx_stock_created", columnList = "created_at")
        }
)
public class StockMovement extends BaseEntity {

    @NotNull
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 50)
    private StockMovementType movementType;

    @NotNull
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @NotNull
    @Column(name = "before_quantity", nullable = false)
    private Integer beforeQuantity;

    @NotNull
    @Column(name = "after_quantity", nullable = false)
    private Integer afterQuantity;


    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType; // ORDER, PURCHASE, ADJUSTMENT

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }
}