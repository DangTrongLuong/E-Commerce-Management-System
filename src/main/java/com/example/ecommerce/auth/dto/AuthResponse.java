package com.example.ecommerce.auth.dto;

import com.example.ecommerce.user.dto.UserResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    @Builder.Default
    private String tokenType = "Bearer";
    private String accessToken;
    private long expiresIn;
    private String refreshToken;
    private UserResponse user;
}
