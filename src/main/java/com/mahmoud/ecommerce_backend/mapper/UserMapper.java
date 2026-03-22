package com.mahmoud.ecommerce_backend.mapper;

import com.mahmoud.ecommerce_backend.dto.user.UserResponse;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.entity.UserRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles", expression = "java(mapRoles(user.getUserRoles()))")
    UserResponse toResponse(User user);

    default List<String> mapRoles(List<UserRole> userRoles) {
        if (userRoles == null) return List.of();
        return userRoles.stream()
                .map(ur -> ur.getRole().getName().name())
                .collect(Collectors.toList());
    }
}