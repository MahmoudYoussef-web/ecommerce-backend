package com.mahmoud.ecommerce_backend.service.product;

import com.mahmoud.ecommerce_backend.dto.product.*;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.ProductMapper;
import com.mahmoud.ecommerce_backend.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductMapper productMapper;


    @Override
    @Transactional
    @CacheEvict(value = {"products", "products_page", "products_search"}, allEntries = true)
    public ProductResponse createProduct(CreateProductRequest request) {

        validateCreateRequest(request);

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        Product product = productMapper.toEntity(request);
        product.setCategory(category);

        productRepository.save(product);

        saveImages(product, request.getImageUrls());

        log.info("Product created | productId={} name={} status={}",
                product.getId(),
                product.getName(),
                product.getStatus());

        return productMapper.toResponse(product);
    }


    @Override
    @Transactional
    @CacheEvict(value = {"products", "products_page", "products_search"}, allEntries = true)
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {

        if (request == null) {
            throw new BadRequestException("Request must not be null");
        }

        Product product = getProductOrThrow(id);

        if (product.isDeleted()) {
            throw new BadRequestException("Cannot update deleted product");
        }

        updateBasicFields(product, request);
        updatePrice(product, request.getPrice());
        updateStock(product, request.getStockQuantity());
        updateCategory(product, request.getCategoryId());

        log.info("Product updated id={}", product.getId());

        return productMapper.toResponse(product);
    }


    @Override
    @Cacheable(value = "products", key = "'id:' + #id", unless = "#result == null")
    public ProductResponse getById(Long id) {
        return productMapper.toResponse(getProductOrThrow(id));
    }

    @Override
    @Cacheable(value = "products_page",
            key = "'all:' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()",
            unless = "#result == null || #result.isEmpty()")
    public Page<ProductResponse> getAll(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(productMapper::toResponse);
    }

    @Override
    @Cacheable(value = "products_page",
            key = "'cat:' + #categoryId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()",
            unless = "#result == null || #result.isEmpty()")
    public Page<ProductResponse> getByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable)
                .map(productMapper::toResponse);
    }


    @Override
    @Transactional
    @CacheEvict(value = {"products", "products_page", "products_search"}, allEntries = true)
    public void deleteProduct(Long id) {

        Product product = getProductOrThrow(id);

        if (product.isDeleted()) {
            throw new BadRequestException("Product already deleted");
        }

        product.setDeleted(true);

        log.info("Product deleted | productId={} action=SOFT_DELETE",
                product.getId());
    }


    @Override
    @Cacheable(value = "products_search",
            key = "'search:' + #name + ':' + #minPrice + ':' + #maxPrice + ':' + #categoryId + ':' + #inStock + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()",
            unless = "#result == null || #result.isEmpty()")
    public Page<ProductResponse> searchProducts(
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Long categoryId,
            Boolean inStock,
            Pageable pageable
    ) {

        validatePriceRange(minPrice, maxPrice);

        Specification<Product> spec = buildSpecification(name, minPrice, maxPrice, categoryId, inStock);

        return productRepository.findAll(spec, pageable)
                .map(productMapper::toResponse);
    }



    private void validateCreateRequest(CreateProductRequest request) {
        if (request == null) throw new BadRequestException("Request must not be null");
        if (request.getCategoryId() == null) throw new BadRequestException("CategoryId must not be null");

        validatePrice(request.getPrice());
        validateStock(request.getStockQuantity());
    }

    private Product getProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void validatePrice(BigDecimal price) {
        if (price != null && price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Price must be greater than zero");
        }
    }

    private void validateStock(Integer stock) {
        if (stock != null && stock < 0) {
            throw new BadRequestException("Stock cannot be negative");
        }
    }

    private void validatePriceRange(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice");
        }
    }

    private void updateBasicFields(Product product, UpdateProductRequest request) {
        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getSlug() != null) product.setSlug(request.getSlug());
        if (request.getSku() != null) product.setSku(request.getSku());
    }

    private void updatePrice(Product product, BigDecimal price) {
        if (price == null) return;
        validatePrice(price);
        product.setPrice(price);
    }

    private void updateStock(Product product, Integer stock) {
        if (stock == null) return;
        validateStock(stock);
        product.setStockQuantity(stock);
    }

    private void updateCategory(Product product, Long categoryId) {
        if (categoryId == null) return;

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        product.setCategory(category);
    }

    private void saveImages(Product product, List<String> urls) {
        if (urls == null || urls.isEmpty()) return;

        List<ProductImage> images = urls.stream()
                .map(url -> ProductImage.builder()
                        .product(product)
                        .url(url)
                        .build())
                .toList();

        productImageRepository.saveAll(images);
    }

    private Specification<Product> buildSpecification(
            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Long categoryId,
            Boolean inStock
    ) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }

            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            if (inStock != null) {
                predicates.add(inStock
                        ? cb.greaterThan(root.get("stockQuantity"), 0)
                        : cb.equal(root.get("stockQuantity"), 0));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}