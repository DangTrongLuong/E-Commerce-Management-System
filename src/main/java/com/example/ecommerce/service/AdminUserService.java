package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.CreateAdminUserRequest;
import com.example.ecommerce.dto.request.UpdateUserRoleRequest;
import com.example.ecommerce.dto.request.UpdateUserStatusRequest;
import com.example.ecommerce.dto.response.UserResponse;
import com.example.ecommerce.entity.AppUser;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.UserStatus;
import com.example.ecommerce.exception.BadRequestExeption;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.exception.DuplicateResourceException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.AppUserRepository;
import com.example.ecommerce.repository.RefreshTokenRepository;
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

    @Transactional
    public UserResponse createUser(CreateAdminUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already exists");
        }

        if (request.getRole() == Role.USER) {
            throw new BadRequestExeption("Admin user endpoint only creates PRODUCT_OWNER or ADMIN");
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

    @Transactional
    public UserResponse updateUserRole(Long userId, UpdateUserRoleRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Protect last active admin
        if (user.getRole() == Role.ADMIN && request.getRole() != Role.ADMIN) {
            long activeAdminCount = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
            if (activeAdminCount <= 1) {
                throw new ConflictException("Cannot demote the last active ADMIN");
            }
        }

        user.setRole(request.getRole());
        user.setTokenVersion((user.getTokenVersion() != null ? user.getTokenVersion() : 1) + 1);
        AppUser updatedUser = userRepository.save(user);

        refreshTokenRepository.deleteByUserId(userId);

        return mapToUserResponse(updatedUser);
    }

    @Transactional
    public UserResponse updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Protect last active admin
        if (user.getRole() == Role.ADMIN && request.getStatus() != UserStatus.ACTIVE) {
            long activeAdminCount = userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
            if (activeAdminCount <= 1) {
                throw new ConflictException("Cannot lock or deactivate the last active ADMIN");
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
