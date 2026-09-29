package com.example.ecommerce.auth.service;

import com.example.ecommerce.auth.dto.LoginRequest;
import com.example.ecommerce.auth.dto.RefreshTokenRequest;
import com.example.ecommerce.auth.dto.RegisterRequest;
import com.example.ecommerce.auth.dto.AuthResponse;
import com.example.ecommerce.user.dto.UserResponse;
import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.auth.entity.RefreshToken;
import com.example.ecommerce.customer.enums.CustomerStatus;
import com.example.ecommerce.user.enums.Role;
import com.example.ecommerce.user.enums.UserStatus;
import com.example.ecommerce.common.exception.AccountLockedException;
import com.example.ecommerce.common.exception.BadRequestExeption;
import com.example.ecommerce.common.exception.DuplicateResourceException;
import com.example.ecommerce.common.exception.TokenExpiredException;
import com.example.ecommerce.user.repository.AppUserRepository;
import com.example.ecommerce.customer.repository.CustomerRepository;
import com.example.ecommerce.auth.repository.RefreshTokenRepository;
import com.example.ecommerce.common.util.JwtUtil;
import com.example.ecommerce.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SecurityUtils securityUtils;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail()) || customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already in use");
        }

        Customer customer = Customer.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(CustomerStatus.ACTIVE)
                .build();
        Customer savedCustomer = customerRepository.save(customer);

        AppUser user = AppUser.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .customer(savedCustomer)
                .failedLoginCount(0)
                .build();
        AppUser savedUser = userRepository.save(user);

        return UserResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .status(savedUser.getStatus())
                .customerId(Long.valueOf(savedCustomer.getId()))
                .createdAt(savedUser.getCreatedAt())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestExeption("Invalid email or password"));

        if (user.getStatus() == UserStatus.LOCKED || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))) {
            throw new AccountLockedException("Account is locked due to too many failed login attempts");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Use a dedicated service bean so REQUIRES_NEW AOP proxy is properly applied
            // (self-invocation within the same bean bypasses Spring's proxy).
            loginAttemptService.recordFailedAttempt(user.getId());
            throw new BadRequestExeption("Invalid email or password");
        }

        // Reset failed login count
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        // Save refresh token hash
        RefreshToken tokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(tokenEntity);

        Long customerId = user.getCustomer() != null ? Long.valueOf(user.getCustomer().getId()) : null;

        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .customerId(customerId)
                .createdAt(user.getCreatedAt())
                .build();

        return AuthResponse.builder()
                .tokenType("Bearer")
                .accessToken(accessToken)
                .expiresIn(jwtUtil.getAccessTokenTtlSeconds())
                .refreshToken(refreshToken)
                .user(userResponse)
                .build();
    }

    @Transactional // try catch
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!jwtUtil.validateToken(refreshToken) || !"refresh".equals(jwtUtil.extractType(refreshToken))) {
            throw new BadRequestExeption("Invalid refresh token");
        }

        String hash = hashToken(refreshToken);
        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestExeption("Refresh token revoked or invalid"));

        if (tokenEntity.isRevoked() || tokenEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Refresh token is expired");
        }

        AppUser user = tokenEntity.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountLockedException("Account is locked or inactive");
        }

        // Token rotation: revoke old token
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);

        // Generate new pair
        String newAccessToken = jwtUtil.generateAccessToken(user);
        String newRefreshToken = jwtUtil.generateRefreshToken(user);

        RefreshToken newTokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(newRefreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(newTokenEntity);

        Long customerId = user.getCustomer() != null ? Long.valueOf(user.getCustomer().getId()) : null;

        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .customerId(customerId)
                .createdAt(user.getCreatedAt())
                .build();

        return AuthResponse.builder()
                .tokenType("Bearer")
                .accessToken(newAccessToken)
                .expiresIn(jwtUtil.getAccessTokenTtlSeconds())
                .refreshToken(newRefreshToken)
                .user(userResponse)
                .build();
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        AppUser currentUser = null;
        try {
            currentUser = securityUtils.getCurrentUser();
        } catch (Exception ignored) {
        }

        if (request != null && request.getRefreshToken() != null) {
            String hash = hashToken(request.getRefreshToken());
            var tokenOpt = refreshTokenRepository.findByTokenHash(hash);
            if (tokenOpt.isPresent()) {
                RefreshToken token = tokenOpt.get();
                token.setRevoked(true);
                refreshTokenRepository.save(token);
                if (currentUser == null) {
                    currentUser = token.getUser();
                }
            }
        }

        if (currentUser != null) {
            int currentVersion = currentUser.getTokenVersion() != null ? currentUser.getTokenVersion() : 1;
            currentUser.setTokenVersion(currentVersion + 1);
            userRepository.save(currentUser);
        }
    }

    public UserResponse getCurrentUserInfo() {
        AppUser user = securityUtils.getCurrentUser();
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

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
