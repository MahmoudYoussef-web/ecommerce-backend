package com.mahmoud.ecommerce_backend.service.product;

import com.mahmoud.ecommerce_backend.dto.product.*;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ForbiddenException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.ProductMapper;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.security.user.CustomUserPrincipal;
import com.mahmoud.ecommerce_backend.service.security.SecurityService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductMapper productMapper;
    private final SecurityService securityService;


    @Override
    @Transactional
    @CacheEvict(value = {"products", "products_page", "products_search"}, allEntries = true)
    public ProductResponse createProduct(CreateProductRequest request) {

        validateCreateRequest(request);

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        Product product = productMapper.toEntity(request);
        product.setCategory(category);
        product.setStatus(request.getStatus() != null ? request.getStatus() : ProductStatus.ACTIVE);
        product.setVendorUserId(resolveVendorOwnership());

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

        enforceVendorOwnership(product);

        updateBasicFields(product, request);
        updatePrice(product, request.getPrice());
        updateStock(product, request.getStockQuantity());
        updateCategory(product, request.getCategoryId());
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }

        log.info("Product updated id={}", product.getId());

        return productMapper.toResponse(product);
    }


    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "'id:' + #id", unless = "#result == null")
    public ProductResponse getById(Long id) {
        return productMapper.toResponse(getProductOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "products_page",
            key = "'all:' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()",
            unless = "#result == null || #result.isEmpty()")
    public Page<ProductResponse> getAll(Pageable pageable) {
        return toBatchedResponses(productRepository.findAll(pageable), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "products_page",
            key = "'cat:' + #categoryId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()",
            unless = "#result == null || #result.isEmpty()")
    public Page<ProductResponse> getByCategory(Long categoryId, Pageable pageable) {
        return toBatchedResponses(productRepository.findByCategoryId(categoryId, pageable), pageable);
    }


    @Override
    @Transactional
    @CacheEvict(value = {"products", "products_page", "products_search"}, allEntries = true)
    public void deleteProduct(Long id) {

        Product product = getProductOrThrow(id);

        if (product.isDeleted()) {
            throw new BadRequestException("Product already deleted");
        }

        enforceVendorOwnership(product);

        product.setDeleted(true);

        log.info("Product deleted | productId={} action=SOFT_DELETE",
                product.getId());
    }


    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "products_search",
            key = "'search:' + #name + ':' + #minPrice + ':' + #maxPrice + ':' + #categoryId + ':' + #inStock + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()",
            unless = "#result == null || #result.isEmpty()")
    public Page<ProductResponse> searchProducts(            String name,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Long categoryId,
            Boolean inStock,
            Pageable pageable
    ) {

        validatePriceRange(minPrice, maxPrice);

        Specification<Product> spec = buildSpecification(name, minPrice, maxPrice, categoryId, inStock);

        return toBatchedResponses(productRepository.findAll(spec, pageable), pageable);
    }

    /**
     * Bounded per-category slices for the homepage. The category table is tiny
     * (top-level storefront categories), each slice is capped by the caller, and
     * the whole payload is cached under "home_sections" so steady-state homepage
     * traffic costs ONE Redis read instead of a size=100 catalog dump.
     */
    /**
     * Bounded per-category slices for the homepage. Deliberately NOT
     * cached: the payload is a handful of indexed queries costing single-digit
     * milliseconds, and caching polymorphic graphs here historically traded a
     * micro-optimization for hard-to-diagnose staleness/failure modes
     * (closure fix: real DTO shape + self-healing error handling retained).
     */
    @Override
    @Transactional(readOnly = true)
    public HomeSectionsResponse getHomeSections(int perCategory) {

        HomeSectionsResponse response = new HomeSectionsResponse();

        for (Category category : categoryRepository.findAll()) {
            if (!category.isActive() || category.getParent() != null) {
                continue;
            }

            PageRequest slice = PageRequest.of(0, Math.max(perCategory, 1));
            Page<ProductResponse> page = toBatchedResponses(
                    productRepository.findByCategoryId(
                            category.getId(),
                            slice),
                    slice);

            if (!page.getContent().isEmpty()) {
                response.getSections().add(new HomeSectionsResponse.Section(
                        category.getName(),
                        new ArrayList<>(page.getContent())));
            }
        }

        return response;
    }

    /**
     * Assembles a page of product DTOs with exactly THREE queries regardless of
     * page size: the page itself, one batched images query, one batched
     * categories query. Lazy per-entity collections are never touched, so the
     * cache-miss path no longer pays a 2×pageSize N+1.
     */
    private Page<ProductResponse> toBatchedResponses(Page<Product> page, Pageable pageable) {

        if (page.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Product> products = page.getContent();

        List<Long> productIds = products.stream().map(Product::getId).toList();

        Map<Long, List<ProductImage>> imagesByProductId = productImageRepository.findByProductIdIn(productIds)
                .stream()
                .collect(Collectors.groupingBy(img -> img.getProduct().getId()));

        Set<Long> categoryIds = products.stream()
                .map(p -> p.getCategory().getId())   // proxy id access — no initialization
                .collect(Collectors.toSet());

        Map<Long, Category> categoriesById = categoryIds.isEmpty()
                ? Map.of()
                : categoryRepository.findAllById(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Function.identity()));

        List<ProductResponse> content = products.stream()
                .map(product -> {
                    Category category = categoriesById.get(product.getCategory().getId());
                    List<String> urls = sortImages(
                            imagesByProductId.getOrDefault(product.getId(), List.of()));
                    return productMapper.toResponse(
                            product,
                            productMapper.toCategoryResponse(category),
                            urls);
                })
                .toList();

        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    private List<String> sortImages(List<ProductImage> images) {
        if (images == null || images.isEmpty()) return List.of();
        return images.stream()
                .sorted(Comparator
                        .comparing(ProductImage::isPrimaryImage).reversed()
                        .thenComparing(ProductImage::getDisplayOrder))
                .map(ProductImage::getUrl)
                .toList();
    }



    private void validateCreateRequest(CreateProductRequest request) {
        if (request == null) throw new BadRequestException("Request must not be null");
        if (request.getCategoryId() == null) throw new BadRequestException("CategoryId must not be null");

        validatePrice(request.getPrice());
        validateStock(request.getStockQuantity());
    }

    /**
     * On creation, a VENDOR principal stamps the product with its own user id;
     * ADMIN (and any non-vendor staff) creates admin-managed products
     * (vendor_user_id NULL — the legacy class, see V6 migration).
     */
    private Long resolveVendorOwnership() {
        CustomUserPrincipal principal = currentPrincipal();
        boolean isVendor = principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_VENDOR".equals(a.getAuthority()));
        return isVendor ? principal.getUserId() : null;
    }

    /**
     * VENDOR may only mutate products it owns. NULL ownership = admin-managed:
     * intentionally off-limits to every vendor so legacy rows can neither be
     * tampered with nor silently claimed. ADMIN bypasses.
     */
    private void enforceVendorOwnership(Product product) {
        CustomUserPrincipal principal = currentPrincipal();

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) {
            return;
        }

        Long ownerId = product.getVendorUserId();
        if (ownerId == null || !ownerId.equals(principal.getUserId())) {
            log.warn("Vendor product mutation denied | productId={} vendorUserId={} requester={}",
                    product.getId(), ownerId, principal.getUserId());
            throw new ForbiddenException("You are not allowed to modify this product");
        }
    }

    private CustomUserPrincipal currentPrincipal() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal()
                instanceof CustomUserPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        return principal;
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