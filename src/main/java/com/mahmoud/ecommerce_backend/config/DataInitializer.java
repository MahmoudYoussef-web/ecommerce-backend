package com.mahmoud.ecommerce_backend.config;

import com.mahmoud.ecommerce_backend.entity.*;
import com.mahmoud.ecommerce_backend.enums.*;
import com.mahmoud.ecommerce_backend.repository.*;
import com.mahmoud.ecommerce_backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Transactional
public class DataInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChartOfAccountRepository coaRepository;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {


        TenantContext.set(1L);

        try {


            Arrays.stream(RoleName.values())
                    .forEach(this::createRoleIfNotExists);

            Role adminRole = getRole(RoleName.ROLE_ADMIN);
            Role customerRole = getRole(RoleName.ROLE_CUSTOMER);


            createUserIfNotExists("admin@gmail.com", adminRole);
            createUserIfNotExists("user@gmail.com", customerRole);


            createAccount("1100", "Accounts Receivable", AccountType.ASSET);
            createAccount("4000", "Sales Revenue", AccountType.REVENUE);

        } finally {
            TenantContext.clear();
        }
    }

    private void createRoleIfNotExists(RoleName roleName) {

        boolean exists = roleRepository.findAll().stream()
                .anyMatch(role -> role.getName() == roleName);

        if (exists) return;

        roleRepository.save(
                Role.builder()
                        .name(roleName)
                        .build()
        );
    }

    private Role getRole(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));
    }

    private void createUserIfNotExists(String email, Role role) {

        String normalizedEmail = email.toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) return;

        User user = User.builder()
                .firstName("Default")
                .lastName("User")
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode("123456"))
                .status(UserStatus.ACTIVE)
                .enabled(true)
                .accountNonLocked(true)
                .emailVerified(true)
                .build();

        userRepository.save(user);

        userRoleRepository.save(
                UserRole.builder()
                        .user(user)
                        .role(role)
                        .build()
        );
    }

    private void createAccount(String code, String name, AccountType type) {
        coaRepository.findByCode(code)
                .orElseGet(() -> coaRepository.save(
                        ChartOfAccount.builder()
                                .code(code)
                                .name(name)
                                .type(type)
                                .build()
                ));
    }
}