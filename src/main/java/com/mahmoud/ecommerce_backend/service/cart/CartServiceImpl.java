package com.mahmoud.ecommerce_backend.service.cart;



import com.mahmoud.ecommerce_backend.dto.cart.*;
import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.CartMapper;
import com.mahmoud.ecommerce_backend.repository.CartItemRepository;
import com.mahmoud.ecommerce_backend.repository.CartRepository;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import com.mahmoud.ecommerce_backend.service.cart.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
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
        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElse(null);

        if (item != null) {
            item.setQuantity(item.getQuantity() + request.getQuantity());
        } else {
            item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .unitPrice(product.getPrice())
                    .quantity(request.getQuantity())
                    .build();
        }

        cartItemRepository.save(item);

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(UpdateCartItemRequest request) {
        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        if (request.getQuantity() <= 0) {
            cartItemRepository.delete(item);
        } else {
            item.setQuantity(request.getQuantity());
        }

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long productId) {
        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        cartItemRepository.delete(item);

        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public void clearCart() {
        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        cart.getCartItems().clear();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Cart createCart(User user) {
        Cart cart = Cart.builder().user(user).build();
        return cartRepository.save(cart);
    }
}
