package com.mahmoud.ecommerce_backend.security.user;

import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.enums.UserStatus;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import com.mahmoud.ecommerce_backend.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        String normalizedEmail = email.toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));


        if (!user.isEmailVerified()) {
            throw new DisabledException("Email not verified");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("Account not active");
        }

        if (!user.isEnabled()) {
            throw new DisabledException("Account disabled");
        }

        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account locked");
        }


        List<String> roles = userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(ur -> ur.getRole().getName().name())
                .toList();

        return CustomUserPrincipal.from(user, roles);
    }
}