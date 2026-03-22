package com.mahmoud.ecommerce_backend.dto.category;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCategoryRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String slug;

    private String description;

    private String imageUrl;
}