package com.mahmoud.ecommerce_backend.service.cart;

import com.mahmoud.ecommerce_backend.dto.cart.*;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.CartMapper;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CartMapper cartMapper;
    private final SecurityService securityService;

    @Override
    public CartResponse getCart() {
        User user = securityService.getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user));

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(AddToCartRequest request) {

        validateAddRequest(request);

        User user = securityService.getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user));

        Product product = getProduct(request.getProductId());
        validateProduct(product);

        ProductVariant variant = resolveVariant(request, product);

        CartItem item = cartItemRepository
                .findByCartIdAndProductIdAndVariantId(
                        cart.getId(),
                        product.getId(),
                        variant != null ? variant.getId() : null
                )
                .orElse(null);

        int newQuantity = (item != null)
                ? item.getQuantity() + request.getQuantity()
                : request.getQuantity();

        validateStock(product, variant, newQuantity);

        if (item != null) {
            item.setQuantity(newQuantity);
            item.setUnitPrice(getPrice(product, variant));
        } else {
            item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .variant(variant)
                    .unitPrice(getPrice(product, variant))
                    .quantity(request.getQuantity())
                    .build();
        }

        cartItemRepository.save(item);
        cart.recalculateTotal();

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(UpdateCartItemRequest request) {

        validateUpdateRequest(request);

        User user = securityService.getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        CartItem item = cartItemRepository
                .findByCartIdAndProductIdAndVariantId(
                        cart.getId(),
                        request.getProductId(),
                        request.getVariantId()
                )
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        Product product = item.getProduct();
        ProductVariant variant = item.getVariant();

        validateProduct(product);

        if (request.getQuantity() == 0) {
            cart.removeItem(request.getProductId(), request.getVariantId());
            return cartMapper.toResponse(cart);
        }

        validateStock(product, variant, request.getQuantity());

        item.setQuantity(request.getQuantity());
        item.setUnitPrice(getPrice(product, variant));

        cart.recalculateTotal();

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long productId, Long variantId) {

        if (productId == null) {
            throw new BadRequestException("ProductId must not be null");
        }

        User user = securityService.getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        cart.removeItem(productId, variantId);

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public void clearCart() {

        User user = securityService.getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        cart.getCartItems().clear();
        cart.recalculateTotal();
    }



    private void validateAddRequest(AddToCartRequest request) {
        if (request == null || request.getProductId() == null) {
            throw new BadRequestException("Invalid request");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }
    }

    private void validateUpdateRequest(UpdateCartItemRequest request) {
        if (request == null || request.getProductId() == null) {
            throw new BadRequestException("Invalid request");
        }
        if (request.getQuantity() == null || request.getQuantity() < 0) {
            throw new BadRequestException("Invalid quantity");
        }
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void validateProduct(Product product) {
        if (product.getStatus() == ProductStatus.DRAFT) {
            throw new BadRequestException("Product is not available");
        }
    }

    private ProductVariant resolveVariant(AddToCartRequest request, Product product) {

        if (request.getVariantId() == null) {
            if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                throw new BadRequestException("Product requires variant selection");
            }
            return null;
        }

        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        if (!variant.getProduct().getId().equals(product.getId())) {
            throw new BadRequestException("Variant does not belong to product");
        }

        return variant;
    }

    private void validateStock(Product product, ProductVariant variant, int quantity) {
        if (variant != null) {
            if (variant.getStockQuantity() < quantity) {
                throw new BadRequestException("Not enough variant stock");
            }
        } else {
            if (product.getStockQuantity() < quantity) {
                throw new BadRequestException("Not enough stock available");
            }
        }
    }

    private java.math.BigDecimal getPrice(Product product, ProductVariant variant) {
        return (variant != null)
                ? variant.getEffectivePrice(product)
                : product.getEffectivePrice();
    }

    private Cart createCart(User user) {
        return cartRepository.save(Cart.builder().user(user).build());
    }
}