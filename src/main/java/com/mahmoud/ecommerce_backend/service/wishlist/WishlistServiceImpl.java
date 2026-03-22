package com.mahmoud.ecommerce_backend.service.wishlist;

import com.mahmoud.ecommerce_backend.dto.wishlist.WishlistResponse;
import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.entity.Wishlist;
import com.mahmoud.ecommerce_backend.entity.WishlistItem;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.WishlistMapper;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import com.mahmoud.ecommerce_backend.repository.WishlistItemRepository;
import com.mahmoud.ecommerce_backend.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final WishlistMapper wishlistMapper;

    @Override
    public WishlistResponse getWishlist() {
        return map(getOrCreateWishlist());
    }

    @Override
    @Transactional
    public WishlistResponse addProduct(Long productId) {
        Wishlist wishlist = getOrCreateWishlist();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        wishlistItemRepository.findByWishlistIdAndProductId(wishlist.getId(), productId)
                .ifPresent(item -> { throw new BadRequestException("Product already in wishlist"); });

        WishlistItem item = WishlistItem.builder()
                .wishlist(wishlist)
                .product(product)
                .build();

        wishlist.addItem(item);

        return map(wishlist);
    }

    @Override
    @Transactional
    public WishlistResponse removeProduct(Long productId) {
        Wishlist wishlist = getOrCreateWishlist();

        WishlistItem item = wishlistItemRepository.findByWishlistIdAndProductId(wishlist.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        wishlist.removeItem(item);

        return map(wishlist);
    }

    private Wishlist getOrCreateWishlist() {
        User user = getCurrentUser();

        return wishlistRepository.findByUserId(user.getId())
                .orElseGet(() -> wishlistRepository.save(Wishlist.builder().user(user).build()));
    }

    private WishlistResponse map(Wishlist wishlist) {
        return WishlistResponse.builder()
                .id(wishlist.getId())
                .items(wishlist.getItems().stream()
                        .map(wishlistMapper::toItemResponse)
                        .toList())
                .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}