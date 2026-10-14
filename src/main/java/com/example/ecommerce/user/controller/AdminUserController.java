package com.example.ecommerce.user.controller;

import com.example.ecommerce.user.dto.CreateAdminUserRequest;
import com.example.ecommerce.user.dto.UpdateUserRoleRequest;
import com.example.ecommerce.user.dto.UpdateUserStatusRequest;
import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.user.dto.UserResponse;
import com.example.ecommerce.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Quản Trị Người Dùng (Admin Users)", description = "Quản trị danh sách tài khoản, phân quyền và trạng thái người dùng (Quyền: ADMIN)")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    @Operation(
            summary = "Tạo mới tài khoản quản trị (PO hoặc ADMIN)",
            description = "Khởi tạo tài khoản đặc quyền mới dành cho Product Owner hoặc Quản trị viên hệ thống với mật khẩu và vai trò được chỉ định. Quyền truy cập: ADMIN."
    )
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateAdminUserRequest request) {
        UserResponse response = adminUserService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo tài khoản người dùng thành công", response));
    }

    @PatchMapping("/{id}/role")
    @Operation(
            summary = "Cập nhật vai trò phân quyền người dùng",
            description = "Thay đổi vai trò (Role: USER, PRODUCT_OWNER, ADMIN) của một tài khoản theo mã ID người dùng. Quyền truy cập: ADMIN."
    )
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        UserResponse response = adminUserService.updateUserRole(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật vai trò người dùng thành công", response));
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Khóa hoặc mở khóa trạng thái tài khoản",
            description = "Cập nhật trạng thái hoạt động của tài khoản (ACTIVE, INACTIVE, LOCKED) theo mã ID người dùng nhằm quản lý quyền đăng nhập. Quyền truy cập: ADMIN."
    )
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        UserResponse response = adminUserService.updateUserStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái người dùng thành công", response));
    }
}
