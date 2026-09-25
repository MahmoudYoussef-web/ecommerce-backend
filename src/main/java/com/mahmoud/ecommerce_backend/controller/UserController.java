package com.mahmoud.ecommerce_backend.controller;

import com.mahmoud.ecommerce_backend.dto.auth.ChangePasswordRequest;
import com.mahmoud.ecommerce_backend.dto.user.UpdateUserRequest;
import com.mahmoud.ecommerce_backend.dto.user.UserResponse;
import com.mahmoud.ecommerce_backend.service.auth.AuthService;
import com.mahmoud.ecommerce_backend.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

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

    /**
     * Authenticated password change. On success every existing session is
     * invalidated (tokenVersion bump + refresh revocation), so the caller must
     * sign in again.
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}