package com.mahmoud.ecommerce_backend.service.product;

import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Whitelisted server-side sort options for public product listings.
 * Prevents clients from sorting by arbitrary entity columns.
 */
public final class ProductSorts {

    private ProductSorts() {
    }

    private static final Map<String, Sort> ALLOWED = Map.of(
            "featured", Sort.unsorted(),
            "price_asc", Sort.by(Sort.Direction.ASC, "price"),
            "price_desc", Sort.by(Sort.Direction.DESC, "price"),
            "name_asc", Sort.by(Sort.Direction.ASC, "name"),
            "name_desc", Sort.by(Sort.Direction.DESC, "name"),
            "rating_desc", Sort.by(Sort.Direction.DESC, "averageRating"),
            "newest", Sort.by(Sort.Direction.DESC, "id")
    );

    public static Sort resolve(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.unsorted();
        }
        Sort resolved = ALLOWED.get(sort.trim().toLowerCase());
        if (resolved == null) {
            throw new BadRequestException(
                    "Unsupported sort: " + sort + ". Allowed: " + String.join(", ", ALLOWED.keySet()));
        }
        return resolved;
    }
}
