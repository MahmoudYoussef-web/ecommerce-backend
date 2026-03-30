package com.mahmoud.ecommerce_backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Where(clause = "is_deleted = false")
@Check(constraints = "price >= 0 AND stock_quantity >= 0")
@Table(
        name = "product_variants",
        indexes = {
                @Index(name = "idx_variant_product", columnList = "product_id"),
                @Index(name = "idx_variant_sku", columnList = "sku", unique = true)
        }
)
public class ProductVariant extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_variant_product")
    )
    private Product product;

    @NotBlank
    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Min(0)
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    @ElementCollection
    @CollectionTable(
            name = "product_variant_attributes",
            joinColumns = @JoinColumn(name = "variant_id")
    )
    @MapKeyColumn(name = "attribute_key")
    @Column(name = "attribute_value")
    private Map<String, String> attributes = new HashMap<>();

    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        if (this.stockQuantity < quantity) {
            throw new IllegalStateException("Insufficient stock for variant");
        }

        this.stockQuantity -= quantity;
    }

    public BigDecimal getEffectivePrice(Product product) {
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            return price;
        }
        return product.getEffectivePrice();
    }
}