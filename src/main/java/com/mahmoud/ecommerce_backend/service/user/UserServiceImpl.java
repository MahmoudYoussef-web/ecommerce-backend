package com.mahmoud.ecommerce_backend.service.user;

import com.mahmoud.ecommerce_backend.dto.user.UpdateUserRequest;
import com.mahmoud.ecommerce_backend.dto.user.UserResponse;
import com.mahmoud.ecommerce_backend.entity.User;
import com.mahmoud.ecommerce_backend.exception.ResourceNotFoundException;
import com.mahmoud.ecommerce_backend.mapper.UserMapper;
import com.mahmoud.ecommerce_backend.repository.UserRepository;
import com.mahmoud.ecommerce_backend.repository.UserRoleRepository;
import com.mahmoud.ecommerce_backend.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final SecurityService securityService;

    @Override
    public UserResponse getCurrentUser() {

        User user = securityService.getCurrentUser();

        return buildUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UpdateUserRequest request) {

        User user = securityService.getCurrentUser();


        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        if (request.getEmail() != null) {
            user.setEmail(request.getEmailNormalized());
        }


        return buildUserResponse(user);
    }


    private UserResponse buildUserResponse(User user) {

        UserResponse response = userMapper.toResponse(user);

        List<String> roles = userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(ur -> ur.getRole().getName().name())
                .toList();

        response.setRoles(roles);

        return response;
    }
}