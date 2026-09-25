package com.mahmoud.ecommerce_backend.dto.product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Cached payload of GET /api/products/home-sections.
 *
 * Deliberately plain mutable POJOs (NOT Map.of/List.of): the payload lives in
 * Redis through the polymorphic-typing serializer, and JDK immutable
 * collection internals (e.g. ImmutableCollections$MapN) either lack accessible
 * constructors or fall outside safe deserialization — they serialize fine but
 * poison the cache on read-back.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HomeSectionsResponse {

    private List<Section> sections = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private String categoryName;
        private List<ProductResponse> products = new ArrayList<>();
    }
}
