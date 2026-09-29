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
@Tag(name = "Admin User Management", description = "Admin endpoints to create PO/ADMIN users and manage roles/status")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    @Operation(summary = "Create PO or ADMIN user (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateAdminUserRequest request) {
        UserResponse response = adminUserService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", response));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Update user role (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        UserResponse response = adminUserService.updateUserRole(id, request);
        return ResponseEntity.ok(ApiResponse.success("User role updated successfully", response));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update user status (ADMIN only)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        UserResponse response = adminUserService.updateUserStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("User status updated successfully", response));
    }
}
