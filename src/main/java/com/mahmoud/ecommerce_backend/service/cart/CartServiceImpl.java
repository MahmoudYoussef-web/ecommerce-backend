package com.mahmoud.ecommerce_backend.service.cart;

import com.mahmoud.ecommerce_backend.dto.cart.*;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.ProductStatus;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.CartMapper;
import com.mahmoud.ecommerce_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;

    @Override
    public CartResponse getCart() {
        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user));

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(AddToCartRequest request) {

        if (request == null || request.getProductId() == null) {
            throw new BadRequestException("Invalid request");
        }

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }

        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getStatus() == ProductStatus.DRAFT) {
            throw new BadRequestException("Product is not available");
        }

        ProductVariant variant = null;

        if (request.getVariantId() != null) {
            variant = productVariantRepository.findById(request.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

            if (!variant.getProduct().getId().equals(product.getId())) {
                throw new BadRequestException("Variant does not belong to product");
            }
        } else if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            throw new BadRequestException("Product requires variant selection");
        }

        CartItem item = cartItemRepository
                .findByCartIdAndProductIdAndVariantId(cart.getId(), product.getId(),
                        variant != null ? variant.getId() : null)
                .orElse(null);

        int newQuantity = request.getQuantity();

        if (item != null) {
            newQuantity = item.getQuantity() + request.getQuantity();
        }

        if (variant != null) {
            if (variant.getStockQuantity() < newQuantity) {
                throw new BadRequestException("Not enough variant stock");
            }
        } else {
            if (product.getStockQuantity() < newQuantity) {
                throw new BadRequestException("Not enough stock available");
            }
        }

        if (item != null) {
            item.setQuantity(newQuantity);
            item.setUnitPrice(
                    variant != null ? variant.getEffectivePrice(product) : product.getEffectivePrice()
            );
        } else {
            item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .variant(variant)
                    .unitPrice(
                            variant != null ? variant.getEffectivePrice(product) : product.getEffectivePrice()
                    )
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

        if (request == null || request.getProductId() == null) {
            throw new BadRequestException("Invalid request");
        }

        if (request.getQuantity() == null || request.getQuantity() < 0) {
            throw new BadRequestException("Invalid quantity");
        }

        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        CartItem item = cartItemRepository
                .findByCartIdAndProductIdAndVariantId(cart.getId(), request.getProductId(), request.getVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        Product product = item.getProduct();
        ProductVariant variant = item.getVariant();

        if (product.getStatus() == ProductStatus.DRAFT) {
            throw new BadRequestException("Product is not available");
        }

        if (request.getQuantity() == 0) {
            cart.removeItem(request.getProductId(), request.getVariantId());
            return cartMapper.toResponse(cart);
        }

        if (variant != null) {
            if (variant.getStockQuantity() < request.getQuantity()) {
                throw new BadRequestException("Not enough variant stock");
            }
        } else {
            if (product.getStockQuantity() < request.getQuantity()) {
                throw new BadRequestException("Not enough stock available");
            }
        }

        item.setQuantity(request.getQuantity());
        item.setUnitPrice(
                variant != null ? variant.getEffectivePrice(product) : product.getEffectivePrice()
        );

        cart.recalculateTotal();

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long productId) {
        throw new UnsupportedOperationException("Use removeItem(productId, variantId)");
    }

    @Transactional
    public CartResponse removeItem(Long productId, Long variantId) {

        if (productId == null) {
            throw new BadRequestException("ProductId must not be null");
        }

        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        cart.removeItem(productId, variantId);

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public void clearCart() {

        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        cart.getCartItems().clear();
        cart.recalculateTotal();
    }

    private User getCurrentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new BadRequestException("Unauthenticated user");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Cart createCart(User user) {
        Cart cart = Cart.builder().user(user).build();
        return cartRepository.save(cart);
    }
}