package com.example.ecommerce.user.service;

import com.example.ecommerce.user.dto.CreateAdminUserRequest;
import com.example.ecommerce.user.dto.UpdateUserRoleRequest;
import com.example.ecommerce.user.dto.UpdateUserStatusRequest;
import com.example.ecommerce.user.dto.UserResponse;
import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.user.enums.Role;
import com.example.ecommerce.user.enums.UserStatus;
import com.example.ecommerce.common.exception.BadRequestExeption;
import com.example.ecommerce.common.exception.ConflictException;
import com.example.ecommerce.common.exception.DuplicateResourceException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.user.repository.AppUserRepository;
import com.example.ecommerce.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(rollbackFor = Exception.class)
    public UserResponse createUser(CreateAdminUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email đã tồn tại trên hệ thống");
        }

        if (request.getRole() == Role.USER) {
            throw new BadRequestExeption("API quản trị chỉ hỗ trợ tạo tài khoản PRODUCT_OWNER hoặc ADMIN");
        }

        AppUser user = AppUser.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .failedLoginCount(0)
                .build();
        AppUser savedUser = userRepository.save(user);

        return mapToUserResponse(savedUser);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserResponse updateUserRole(Long userId, UpdateUserRoleRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã ID: " + userId));

        // Protect last active admin
        if (user.getRole() == Role.ADMIN && request.getRole() != Role.ADMIN) {
            long activeAdminCount = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
            if (activeAdminCount <= 1) {
                throw new ConflictException("Không thể hạ cấp tài khoản ADMIN đang hoạt động duy nhất");
            }
        }

        user.setRole(request.getRole());
        user.setTokenVersion((user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1);
        AppUser updatedUser = userRepository.save(user);

        refreshTokenRepository.deleteByUserId(userId);

        return mapToUserResponse(updatedUser);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserResponse updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã ID: " + userId));

        // Protect last active admin
        if (user.getRole() == Role.ADMIN && request.getStatus() != UserStatus.ACTIVE) {
            long activeAdminCount = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
            if (activeAdminCount <= 1) {
                throw new ConflictException("Không thể khóa hoặc ngưng hoạt động tài khoản ADMIN đang hoạt động duy nhất");
            }
        }

        user.setStatus(request.getStatus());
        user.setTokenVersion((user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1);
        if (request.getStatus() == UserStatus.ACTIVE) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }
        AppUser updatedUser = userRepository.save(user);

        // Revoke all refresh tokens
        refreshTokenRepository.deleteByUserId(userId);

        return mapToUserResponse(updatedUser);
    }

    private UserResponse mapToUserResponse(AppUser user) {
        Long customerId = user.getCustomer() != null ? Long.valueOf(user.getCustomer().getId()) : null;
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .customerId(customerId)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
