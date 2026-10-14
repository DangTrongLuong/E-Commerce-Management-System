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
@Tag(name = "Xác Thực & Tài Khoản (Authentication)", description = "Đăng ký, đăng nhập, kích hoạt tài khoản qua email và quản lý JWT token (Quyền: Công khai & Đã đăng nhập)")
public class AuthController {

    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    @Operation(
            summary = "Đăng ký tài khoản người dùng mới",
            description = "Tạo tài khoản khách hàng mới với vai trò mặc định USER. Hệ thống tự động tạo mã xác thực và gửi email kích hoạt tài khoản tới người dùng. Quyền truy cập: Công khai."
    )
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký tài khoản thành công", response));
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    @Operation(
            summary = "Đăng nhập hệ thống lấy JWT Token",
            description = "Xác thực danh tính bằng email/username và mật khẩu. Trả về cặp Access Token (JWT) và Refresh Token nếu tài khoản đã được kích hoạt. Quyền truy cập: Công khai."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    @GetMapping("/activate-account")
    @PreAuthorize("permitAll()")
    @Operation(
            summary = "Kích hoạt tài khoản bằng liên kết từ Email",
            description = "Xác thực mã kích hoạt (activation token) từ liên kết người dùng nhấn trong email. Nếu hợp lệ, kích hoạt tài khoản thành ACTIVE và chuyển hướng người dùng về trang giao diện Frontend tương ứng. Quyền truy cập: Công khai."
    )
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
    @Operation(
            summary = "Gửi lại liên kết kích hoạt tài khoản qua email",
            description = "Cấp mới token kích hoạt và gửi lại thư xác thực vào email đã đăng ký của người dùng trong trường hợp token cũ hết hạn hoặc bị thất lạc. Quyền truy cập: Công khai."
    )
    public ResponseEntity<ApiResponse<Void>> resendVerificationCode(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationCode(request);
        return ResponseEntity.ok(ApiResponse
                .success("Đã gửi lại liên kết kích hoạt thành công. Vui lòng kiểm tra hộp thư email của bạn.", null));
    }

    @PostMapping("/refresh")
    @PreAuthorize("permitAll()")
    @Operation(
            summary = "Làm mới Access Token bằng Refresh Token",
            description = "Cấp một Access Token JWT mới khi Access Token cũ đã hết hạn thông qua Refresh Token hợp lệ và chưa bị thu hồi. Quyền truy cập: Công khai."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Làm mới token thành công", response));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Đăng xuất khỏi hệ thống",
            description = "Thu hồi hiệu lực của Refresh Token hiện tại và xóa phiên đăng nhập của người dùng. Quyền truy cập: Đã đăng nhập."
    )
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Lấy thông tin tài khoản người dùng đang đăng nhập",
            description = "Truy vấn thông tin hồ sơ của người dùng hiện tại dựa trên JWT Access Token đính kèm trong Authorization Header. Quyền truy cập: Đã đăng nhập."
    )
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UserResponse response = authService.getCurrentUserInfo();
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin người dùng thành công", response));
    }

}
