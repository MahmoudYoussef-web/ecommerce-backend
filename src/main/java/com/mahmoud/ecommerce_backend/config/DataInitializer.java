package com.mahmoud.ecommerce_backend.config;

import com.mahmoud.ecommerce_backend.entity.Role;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.entity.UserRole;
import com.mahmoud.ecommerce_backend.enums.RoleName;
import com.mahmoud.ecommerce_backend.repository.RoleRepository;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DataInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {

        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(RoleName.ROLE_ADMIN)
                        .build()));

        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(RoleName.ROLE_CUSTOMER)
                        .build()));

        createUserIfNotExists("admin@gmail.com", adminRole);
        createUserIfNotExists("user@gmail.com", customerRole);
    }

    private void createUserIfNotExists(String email, Role role) {

        if (userRepository.existsByEmail(email)) return;

        User user = User.builder()
                .firstName("Default")
                .lastName("User")
                .email(email)
                .passwordHash(passwordEncoder.encode("123456"))
                .build();

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .build();

        user.getUserRoles().add(userRole);

        userRepository.save(user);
    }
}