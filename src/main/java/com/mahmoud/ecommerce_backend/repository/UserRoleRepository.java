package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {


    List<UserRole> findByUser(User user);


    List<UserRole> findByUserId(Long userId);
}