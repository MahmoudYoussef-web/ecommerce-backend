package com.mahmoud.ecommerce_backend.security.user;

import com.mahmoud.ecommerce_backend.entity.Tenant;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import com.mahmoud.ecommerce_backend.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        String normalizedEmail = email.toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Tenant tenant = user.getTenant();

        if (tenant != null && !tenant.isActive()) {
            throw new UsernameNotFoundException("User not found");
        }


        List<String> roles = userRoleRepository.findByUserIdWithRoles(user.getId())
                .stream()
                .map(ur -> ur.getRole().getName().name())
                .toList();

        return CustomUserPrincipal.from(user, roles);
    }
}
