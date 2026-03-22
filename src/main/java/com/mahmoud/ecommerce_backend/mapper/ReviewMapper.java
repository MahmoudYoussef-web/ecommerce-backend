package com.mahmoud.ecommerce_backend.mapper;

import com.mahmoud.ecommerce_backend.dto.review.CreateReviewRequest;
import com.mahmoud.ecommerce_backend.dto.review.ReviewResponse;
import com.mahmoud.ecommerce_backend.entity.Review;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", expression = "java(review.getUser().getFullName())")
    ReviewResponse toResponse(Review review);

    Review toEntity(CreateReviewRequest request);
}