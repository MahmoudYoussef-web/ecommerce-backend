package com.mahmoud.ecommerce_backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Where;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Where(clause = "is_deleted = false")
@Table(
        name = "wishlist_items",
        indexes = {
                @Index(name = "idx_wishlist_item_wishlist", columnList = "wishlist_id"),
                @Index(name = "idx_wishlist_item_product", columnList = "product_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_wishlist_item_wishlist_product",
                        columnNames = {"wishlist_id", "product_id"}
                )
        }
)
public class WishlistItem extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "wishlist_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_wishlist_item_wishlist")
    )
    private Wishlist wishlist;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_wishlist_item_product")
    )
    private Product product;

    @Column(name = "notes", length = 500)
    private String notes;
}