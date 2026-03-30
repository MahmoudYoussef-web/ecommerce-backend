package com.mahmoud.ecommerce_backend.service.product;

import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = {"products"})
public class ProductCacheService {

    private final ProductRepository productRepository;

    @Cacheable(key = "'product:' + #id", unless = "#result == null")
    public Product getById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    @Cacheable(
            cacheNames = "products_page",
            key = "'page:' + #page + ':size:' + #size + ':status:ACTIVE'",
            unless = "#result == null || #result.isEmpty()"
    )
    public Page<Product> getActiveProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByStatus(ProductStatus.ACTIVE, pageable);
    }

    @Caching(put = {
            @CachePut(key = "'product:' + #result.id")
    }, evict = {
            @CacheEvict(cacheNames = "products_page", allEntries = true)
    })
    public Product save(Product product) {
        return productRepository.save(product);
    }

    @Caching(evict = {
            @CacheEvict(key = "'product:' + #id"),
            @CacheEvict(cacheNames = "products_page", allEntries = true)
    })
    public void evictById(Long id) {
    }

    @CacheEvict(cacheNames = {"products", "products_page"}, allEntries = true)
    public void evictAll() {
    }
}