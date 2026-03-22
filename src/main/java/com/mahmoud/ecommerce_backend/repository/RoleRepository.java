package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mahmoud.ecommerce_backend.entity.Role;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
