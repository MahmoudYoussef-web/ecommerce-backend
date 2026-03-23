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

        if (request == null) {
            throw new BadRequestException("Request must not be null");
        }

        if (request.getProductId() == null) {
            throw new BadRequestException("ProductId must not be null");
        }

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BadRequestException("Quantity must be greater than zero");
        }

        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> {
                    log.warn("Product not found id={}", request.getProductId());
                    return new ResourceNotFoundException("Product not found");
                });

        if (product.getStatus() == ProductStatus.DRAFT) {
            throw new BadRequestException("Product is not available");
        }

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElse(null);

        int newQuantity = request.getQuantity();

        if (item != null) {
            newQuantity = item.getQuantity() + request.getQuantity();
        }

        if (product.getStockQuantity() < newQuantity) {
            log.warn("Stock exceeded productId={}, requested={}, available={}",
                    product.getId(), newQuantity, product.getStockQuantity());

            throw new BadRequestException("Not enough stock available");
        }

        if (item != null) {
            item.setQuantity(newQuantity);
            item.setUnitPrice(product.getEffectivePrice());
        } else {
            item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .unitPrice(product.getEffectivePrice())
                    .quantity(request.getQuantity())
                    .build();
        }

        cartItemRepository.save(item);

        log.info("Item added to cart userId={}, productId={}, quantity={}",
                user.getId(), product.getId(), item.getQuantity());

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(UpdateCartItemRequest request) {

        if (request == null) {
            throw new BadRequestException("Request must not be null");
        }

        if (request.getProductId() == null) {
            throw new BadRequestException("ProductId must not be null");
        }

        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        Product product = item.getProduct();

        if (product == null) {
            throw new BadRequestException("Invalid cart item");
        }

        if (product.getStatus() == ProductStatus.DRAFT) {
            throw new BadRequestException("Product is not available");
        }

        if (request.getQuantity() == null || request.getQuantity() < 0) {
            throw new BadRequestException("Invalid quantity");
        }

        if (request.getQuantity() == 0) {
            cartItemRepository.delete(item);
            return cartMapper.toResponse(cart);
        }

        if (product.getStockQuantity() < request.getQuantity()) {
            throw new BadRequestException("Not enough stock available");
        }

        item.setQuantity(request.getQuantity());
        item.setUnitPrice(product.getEffectivePrice());

        log.info("Cart item updated userId={}, productId={}, quantity={}",
                user.getId(), product.getId(), item.getQuantity());

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long productId) {

        if (productId == null) {
            throw new BadRequestException("ProductId must not be null");
        }

        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        cartItemRepository.delete(item);

        log.info("Item removed userId={}, productId={}", user.getId(), productId);

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public void clearCart() {

        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        cart.getCartItems().clear();

        log.info("Cart cleared userId={}", user.getId());
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