package com.example.ecommerce.auth.controller;

import com.example.ecommerce.auth.dto.LoginRequest;
import com.example.ecommerce.auth.dto.RefreshTokenRequest;
import com.example.ecommerce.auth.dto.RegisterRequest;
import com.example.ecommerce.auth.dto.ResendVerificationRequest;
import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.auth.dto.AuthResponse;
import com.example.ecommerce.user.dto.UserResponse;
import com.example.ecommerce.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and User Info Endpoints")
public class AuthController {

    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Register new user account (Public)")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký tài khoản thành công", response));
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Login to obtain JWT access & refresh tokens (Public)")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    @GetMapping("/activate-account")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Kích hoạt tài khoản bằng liên kết từ Email và chuyển hướng về Frontend (Public)")
    public void activateAccount(
            @RequestParam("token") String token,
            HttpServletResponse response) throws IOException {
        try {
            UserResponse user = authService.activateAccountByToken(token);
            String encodedEmail = URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8);
            response.sendRedirect(frontendUrl + "/activate-success?email=" + encodedEmail + "&status=success");
        } catch (Exception e) {
            String encodedMsg = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            response.sendRedirect(frontendUrl + "/activate-failed?error=" + encodedMsg);
        }
    }

    @PostMapping("/resend-verification")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Gửi lại liên kết kích hoạt tài khoản qua email (Public)")
    public ResponseEntity<ApiResponse<Void>> resendVerificationCode(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationCode(request);
        return ResponseEntity.ok(ApiResponse
                .success("Đã gửi lại liên kết kích hoạt thành công. Vui lòng kiểm tra hộp thư email của bạn.", null));
    }

    @PostMapping("/refresh")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Refresh access token (Public)")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Làm mới token thành công", response));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Logout user and revoke refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current authenticated user info")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UserResponse response = authService.getCurrentUserInfo();
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin người dùng thành công", response));
    }

}
