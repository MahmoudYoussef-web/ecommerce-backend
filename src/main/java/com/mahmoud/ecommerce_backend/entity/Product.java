package com.mahmoud.ecommerce_backend.entity;

import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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
@Check(constraints = "price > 0 AND stock_quantity >= 0")
@Table(
        name = "products",
        indexes = {
                @Index(name = "idx_product_slug", columnList = "slug", unique = true),
                @Index(name = "idx_product_sku", columnList = "sku", unique = true),
                @Index(name = "idx_product_category", columnList = "category_id"),
                @Index(name = "idx_product_status", columnList = "status"),
                @Index(name = "idx_product_price", columnList = "price"),
                @Index(name = "idx_product_featured", columnList = "featured"),
                @Index(name = "idx_product_created", columnList = "created_at")
        }
)
public class Product extends BaseEntity {

    @NotBlank
    @Size(min = 2, max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @NotBlank
    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @NotBlank
    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String shortDescription;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 10, fraction = 2)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @DecimalMin("0.00")
    @Column(precision = 12, scale = 2)
    private BigDecimal discountedPrice;

    @Min(0)
    @Column(nullable = false)
    private Integer stockQuantity = 0;

    @Min(0)
    @Column(nullable = false)
    private Integer lowStockThreshold = 5;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.DRAFT;

    @DecimalMin("0.000")
    @Column(precision = 8, scale = 3)
    private BigDecimal weightKg;

    @Column(length = 100)
    private String brand;

    @DecimalMin("0.00")
    @DecimalMax("5.00")
    @Column(precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(nullable = false)
    private Integer reviewCount = 0;

    @Column(nullable = false)
    private boolean featured = false;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "category_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_category")
    )
    private Category category;

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductImage> images = new ArrayList<>();


    @Builder.Default
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<Review> reviews = new ArrayList<>();


    public BigDecimal getEffectivePrice() {
        if (discountedPrice != null &&
                discountedPrice.compareTo(BigDecimal.ZERO) > 0 &&
                discountedPrice.compareTo(price) < 0) {
            return discountedPrice;
        }
        return price;
    }
}