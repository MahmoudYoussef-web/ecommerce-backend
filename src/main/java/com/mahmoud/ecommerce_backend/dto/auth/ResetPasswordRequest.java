package com.mahmoud.ecommerce_backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    @Size(max = 255)
    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String newPassword;
}
