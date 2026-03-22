package com.mahmoud.ecommerce_backend.service.review;

import com.mahmoud.ecommerce_backend.dto.review.CreateReviewRequest;
import com.mahmoud.ecommerce_backend.dto.review.ReviewResponse;
import com.mahmoud.ecommerce_backend.entity.Product;
import com.mahmoud.ecommerce_backend.entity.Review;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.exception.BadRequestException;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.ReviewMapper;
import com.mahmoud.ecommerce_backend.repository.ProductRepository;
import com.mahmoud.ecommerce_backend.repository.ReviewRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ReviewMapper reviewMapper;

    @Override
    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request) {

        User user = getCurrentUser();

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        reviewRepository.findByUserIdAndProductId(user.getId(), product.getId())
                .ifPresent(r -> { throw new BadRequestException("Already reviewed"); });

        Review review = reviewMapper.toEntity(request);
        review.setUser(user);
        review.setProduct(product);

        reviewRepository.save(review);

        return reviewMapper.toResponse(review);
    }

    @Override
    public List<ReviewResponse> getProductReviews(Long productId) {
        return reviewRepository.findByProductIdAndApprovedTrue(productId)
                .stream()
                .map(reviewMapper::toResponse)
                .toList();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}