package com.mahmoud.ecommerce_backend.service.user;

import com.mahmoud.ecommerce_backend.dto.user.UpdateUserRequest;
import com.mahmoud.ecommerce_backend.dto.user.UserResponse;

public interface UserService {

    UserResponse getCurrentUser();

    UserResponse updateProfile(UpdateUserRequest request);
}