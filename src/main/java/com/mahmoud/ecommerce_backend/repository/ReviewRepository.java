package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdAndApprovedTrue(Long productId);

    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

    @Query("select count(r) from Review r where r.product.id = :productId and r.approved = true")
    long countApprovedByProductId(@Param("productId") Long productId);

    @Query("select coalesce(avg(r.rating), 0.0) from Review r where r.product.id = :productId and r.approved = true")
    Double averageApprovedRating(@Param("productId") Long productId);
}
