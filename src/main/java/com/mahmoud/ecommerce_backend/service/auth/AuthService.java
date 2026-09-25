package com.mahmoud.ecommerce_backend.service.auth;

import com.mahmoud.ecommerce_backend.dto.auth.*;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthTokens login(LoginRequest request);

    AuthTokens refreshToken(String rawToken);

    void logout(String rawToken);

    void verifyEmail(String token);

    void changePassword(ChangePasswordRequest request);

    void requestPasswordReset(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
