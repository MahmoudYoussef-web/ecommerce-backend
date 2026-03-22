package com.mahmoud.ecommerce_backend.dto.category;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCategoryRequest {

    private String name;

    private String slug;

    private String description;

    private String imageUrl;
}