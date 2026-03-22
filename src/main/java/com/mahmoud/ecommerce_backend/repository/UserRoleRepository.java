package com.mahmoud.ecommerce_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;




import com.mahmoud.ecommerce_backend.entity.UserRole;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
}
