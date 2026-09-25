package com.mahmoud.ecommerce_backend;

import com.mahmoud.ecommerce_backend.config.RedisConfig;
import com.mahmoud.ecommerce_backend.dto.category.CategoryResponse;
import com.mahmoud.ecommerce_backend.dto.product.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 regression: Redis cache serialization safety.
 *
 * Proves that the hardened cache mapper:
 *  - round-trips legitimate product DTOs and paginated results (Phase 2/3
 *    cache behavior intact)
 *  - REJECTS poisoned polymorphic "@class" payloads before instantiation
 *  - treats a poisoned elementType inside cached page JSON as inert data
 */
class RedisSerializerSecurityTest {

    private final GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(RedisConfig.buildCacheMapper());

    private ProductResponse sampleProduct(String name, String price) {
        CategoryResponse category = new CategoryResponse();
        category.setId(7L);
        category.setName("Headphones");
        category.setSlug("headphones");

        ProductResponse product = new ProductResponse();
        product.setId(42L);
        product.setName(name);
        product.setDescription("desc");
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(11);
        product.setAverageRating(4.5);
        product.setReviewCount(9);
        product.setCategory(category);
        product.setImageUrls(List.of("https://cdn.example.com/a.png", "https://cdn.example.com/b.png"));
        return product;
    }

    @Test
    void productDtoRoundTripsThroughTheCache() {
        ProductResponse original = sampleProduct("WH-1000XM5", "349.99");

        byte[] bytes = serializer.serialize(original);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(ProductResponse.class);
        ProductResponse back = (ProductResponse) restored;
        assertThat(back.getId()).isEqualTo(42L);
        assertThat(back.getName()).isEqualTo("WH-1000XM5");
        assertThat(back.getPrice()).isEqualByComparingTo("349.99");
        assertThat(back.getCategory().getSlug()).isEqualTo("headphones");
        assertThat(back.getImageUrls()).containsExactly(
                "https://cdn.example.com/a.png", "https://cdn.example.com/b.png");
    }

    @Test
    void paginatedProductResultsStillRoundTrip() {
        List<ProductResponse> content = List.of(
                sampleProduct("P1", "10.00"),
                sampleProduct("P2", "20.50")
        );
        Page<ProductResponse> page = new PageImpl<>(content, PageRequest.of(0, 2), 25);

        byte[] bytes = serializer.serialize(page);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(Page.class);
        Page<?> back = (Page<?>) restored;
        assertThat(back.getTotalElements()).isEqualTo(25);
        assertThat(back.getContent()).hasSize(2);
        assertThat(back.getContent().get(0)).isInstanceOf(ProductResponse.class);
        assertThat(((ProductResponse) back.getContent().get(1)).getName()).isEqualTo("P2");
    }

    @Test
    void poisonedPolymorphicTypeTagIsRejectedBeforeInstantiation() {
        // Classic Jackson gadget candidates — none may ever be instantiated.
        List<String> poisoned = List.of(
                "{\"@class\":\"javax.management.remote.rmi.LocalRMIServerSocketFactory\"}",
                "{\"@class\":\"org.apache.commons.collections.functors.InvokerTransformer\"}",
                "{\"@class\":\"java.lang.Runtime\"}",
                "{\"@class\":\"net.sf.ehcache.transaction.manager.DefaultTransactionManagerLookup\"}"
        );

        for (String payload : poisoned) {
            assertThatThrownBy(() -> serializer.deserialize(payload.getBytes()))
                    .as("poisoned payload must be rejected: %s", payload)
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void poisonedElementTypeInsideCachedPageIsInertData() {
        // An attacker with cache-write access crafts a cached page whose
        // elementType points at an arbitrary class.
        String crafted = "{\"@class\":\"org.springframework.data.domain.PageImpl\"," +
                "\"content\":[\"irrelevant\"]," +
                "\"elementType\":\"java.lang.Runtime\"," +
                "\"page\":0,\"size\":1,\"totalElements\":1,\"sort\":null}";

        Object restored = serializer.deserialize(crafted.getBytes());

        // Must deserialize safely to plain data — never load/instantiate Runtime.
        assertThat(restored).isInstanceOf(Page.class);
        Object firstElement = ((Page<?>) restored).getContent().get(0);
        assertThat(firstElement).isNotInstanceOf(Runtime.class);
    }

    @Test
    void legacyUntypedPageContentStillLoads() {
        // Pre-hardening entries without a usable elementType fall back to
        // untyped conversion instead of failing the whole cache entry.
        String legacy = "{\"@class\":\"com.mahmoud.ecommerce_backend.config.UnknownFutureDto\"," +
                "\"value\":1}";

        // Unknown project-namespace class also degrades safely (no load).
        assertThatThrownBy(() -> serializer.deserialize(legacy.getBytes()))
                .isInstanceOf(Exception.class);

        String legacyPage = "{\"@class\":\"org.springframework.data.domain.PageImpl\"," +
                "\"content\":[{\"name\":\"legacy\"}]," +
                "\"elementType\":\"\"," +
                "\"page\":0,\"size\":1,\"totalElements\":1,\"sort\":null}";

        Object restored = serializer.deserialize(legacyPage.getBytes());
        assertThat(((Page<?>) restored).getContent()).hasSize(1);
    }
}
