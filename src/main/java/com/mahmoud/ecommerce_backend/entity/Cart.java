package com.mahmoud.ecommerce_backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Where(clause = "is_deleted = false")
@Check(constraints = "total_amount >= 0")
@Table(
        name = "carts",
        indexes = {
                @Index(name = "idx_cart_user", columnList = "user_id", unique = true),
                @Index(name = "idx_cart_created", columnList = "created_at")
        }
)
public class Cart extends BaseEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_cart_user")
    )
    private User user;

    @Builder.Default
    @OneToMany(
            mappedBy = "cart",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<CartItem> cartItems = new ArrayList<>();

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @NotNull
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;


    public int getItemCount() {
        return cartItems.stream()
                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                .sum();
    }


    public void recalculateTotal() {
        this.totalAmount = cartItems.stream()
                .map(item -> item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    public void addItem(CartItem item) {
        if (item == null) return;

        for (CartItem existing : cartItems) {
            if (existing.getProduct().getId().equals(item.getProduct().getId())) {
                existing.setQuantity(existing.getQuantity() + item.getQuantity());
                existing.syncTotalPrice();
                recalculateTotal();
                return;
            }
        }

        cartItems.add(item);
        item.setCart(this);
        recalculateTotal();
    }

    public void removeItem(CartItem item) {
        if (item == null) return;

        cartItems.remove(item);
        item.setCart(null);
        recalculateTotal();
    }
}