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
    @Mapping(target = "title", source = "title")
    @Mapping(target = "comment", source = "body")
    @Mapping(target = "verifiedPurchase", source = "verifiedPurchase")
    @Mapping(target = "helpfulVotes", source = "helpfulVotes")
    ReviewResponse toResponse(Review review);

    @Mapping(target = "body", source = "comment")
    Review toEntity(CreateReviewRequest request);
}
