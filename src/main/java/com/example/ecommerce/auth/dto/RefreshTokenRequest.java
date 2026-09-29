package com.example.ecommerce.auth.dto;

import com.example.ecommerce.auth.entity.RefreshToken;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RefreshTokenRequest {

    @NotBlank(message = "RefreshToken is required")
    private String refreshToken;
}
