package com.mahmoud.ecommerce_backend.dto.wishlist;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistResponse {
    private Long id;
    private List<WishlistItemResponse> items;
}