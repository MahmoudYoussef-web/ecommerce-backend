package com.mahmoud.ecommerce_backend.repository;

import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {


    List<UserRole> findByUser(User user);


    List<UserRole> findByUserId(Long userId);


    @Query("select ur from UserRole ur join fetch ur.role where ur.user.id = :userId")
    List<UserRole> findByUserIdWithRoles(@Param("userId") Long userId);
}