package com.mahmoud.ecommerce_backend.event.listener;

import com.mahmoud.ecommerce_backend.event.inventory.ProductStockChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts product caches AFTER the authoritative stock transaction commits.
 *
 * Runs synchronously in the after-commit phase: each eviction is a cheap Redis
 * DEL, and running inline keeps strict ordering (commit → evict → next read).
 * Only the affected product's detail entry is removed; page/search/section
 * caches are cleared because they embed stockQuantity snapshots.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCacheInvalidator {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductStockChanged(ProductStockChangedEvent event) {

        Long productId = event.productId();

        Cache detail = cacheManager.getCache("products");
        if (detail != null) {
            detail.evict("id:" + productId);
        }

        clear("products_page");
        clear("products_search");
        clear("home_sections");

        log.info("Product cache evicted after stock change | productId={}", productId);
    }

    private void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
