package com.mahmoud.ecommerce_backend.service.security;

import com.mahmoud.ecommerce_backend.entity.User;

public interface SecurityService {

    User getCurrentUser();

    Long getCurrentUserId();
}