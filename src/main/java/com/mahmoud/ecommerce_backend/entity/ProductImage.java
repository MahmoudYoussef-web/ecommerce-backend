package com.mahmoud.ecommerce_backend.entity;

import com.mahmoud.ecommerce_backend.enums.ImageType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
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
        name = "product_images",
        indexes = {
                @Index(name = "idx_product_image_product", columnList = "product_id"),
                @Index(name = "idx_product_image_type", columnList = "image_type"),
                @Index(name = "idx_product_image_primary", columnList = "primary_image")
        }
)
public class ProductImage extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_image_product")
    )
    private Product product;

    @NotBlank
    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 20)
    private ImageType imageType = ImageType.GALLERY;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "primary_image", nullable = false)
    private boolean primaryImage = false;
}