package com.mahmoud.ecommerce_backend.service.security;

import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import com.mahmoud.ecommerce_backend.security.user.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityServiceImpl implements SecurityService {

    private final UserRepository userRepository;

    @Override
    public User getCurrentUser() {
        Long userId = getCurrentUserId();

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    @Override
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {

            throw new IllegalStateException("No authenticated user");
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof CustomUserPrincipal customUser)) {
            throw new IllegalStateException("Invalid authentication principal");
        }

        return customUser.getUserId();
    }
}