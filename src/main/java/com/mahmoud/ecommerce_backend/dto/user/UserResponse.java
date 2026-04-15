package com.mahmoud.ecommerce_backend.dto.user;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;

    private String email;


    private String fullName;

    private List<String> roles;


    private String phoneNumber;
}