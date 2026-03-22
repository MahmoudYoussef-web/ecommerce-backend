package com.mahmoud.ecommerce_backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Where;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Where(clause = "is_deleted = false")
@Check(constraints = "rating BETWEEN 1 AND 5")
@Table(
        name = "reviews",
        indexes = {
                @Index(name = "idx_review_product", columnList = "product_id"),
                @Index(name = "idx_review_user", columnList = "user_id"),
                @Index(name = "idx_review_rating", columnList = "rating"),
                @Index(name = "idx_review_approved", columnList = "approved"),
                @Index(name = "idx_review_created", columnList = "created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_review_user_product",
                        columnNames = {"user_id", "product_id"}
                )
        }
)
public class Review extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_review_product")
    )
    private Product product;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_review_user")
    )
    private User user;

    @NotNull
    @Min(1)
    @Max(5)
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Size(max = 200)
    @Column(name = "title", length = 200)
    private String title;

    @Size(max = 3000)
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase = false;

    @Column(name = "helpful_votes", nullable = false)
    private Integer helpfulVotes = 0;

    @Column(name = "approved", nullable = false)
    private boolean approved = false;


    @PrePersist
    public void beforeCreate() {
        if (helpfulVotes == null) {
            helpfulVotes = 0;
        }
    }
}