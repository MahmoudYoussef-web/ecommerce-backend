package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.dto.user.UpdateUserRequest;
import com.mahmoud.ecommerce_backend.dto.user.UserResponse;
import com.mahmoud.ecommerce_backend.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public UserResponse getCurrentUser() {
        return userService.getCurrentUser();
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me")
    public UserResponse update(@Valid @RequestBody UpdateUserRequest request) {
        return userService.updateProfile(request);
    }
}