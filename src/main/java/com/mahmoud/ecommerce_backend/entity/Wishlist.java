package com.mahmoud.ecommerce_backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Where;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Where(clause = "is_deleted = false")
@Table(
        name = "wishlists",
        indexes = {
                @Index(name = "idx_wishlist_user", columnList = "user_id", unique = true)
        }
)
public class Wishlist extends BaseEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_wishlist_user")
    )
    private User user;

    @Builder.Default
    @OneToMany(
            mappedBy = "wishlist",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<WishlistItem> items = new ArrayList<>();


    public void addItem(WishlistItem item) {
        if (item == null) return;

        for (WishlistItem existing : items) {
            if (existing.getProduct().getId().equals(item.getProduct().getId())) {
                return; // already exists
            }
        }

        items.add(item);
        item.setWishlist(this);
    }

    public void removeItem(WishlistItem item) {
        if (item == null) return;

        items.remove(item);
        item.setWishlist(null);
    }
}