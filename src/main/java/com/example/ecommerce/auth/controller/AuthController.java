package com.example.ecommerce.auth.controller;

import com.example.ecommerce.auth.dto.LoginRequest;
import com.example.ecommerce.auth.dto.RefreshTokenRequest;
import com.example.ecommerce.auth.dto.RegisterRequest;
import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.auth.dto.AuthResponse;
import com.example.ecommerce.user.dto.UserResponse;
import com.example.ecommerce.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and User Info Endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Register new user account (Public)")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Login to obtain JWT access & refresh tokens (Public)")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Refresh access token (Public)")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refresh successful", response));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Logout user and revoke refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current authenticated user info")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UserResponse response = authService.getCurrentUserInfo();
        return ResponseEntity.ok(ApiResponse.success("Current user info fetched successfully", response));
    }
}
