package com.example.ecommerce.auth.service;

import com.example.ecommerce.auth.dto.LoginRequest;
import com.example.ecommerce.auth.dto.RefreshTokenRequest;
import com.example.ecommerce.auth.dto.RegisterRequest;
import com.example.ecommerce.auth.dto.ResendVerificationRequest;
import com.example.ecommerce.auth.dto.AuthResponse;
import com.example.ecommerce.user.dto.UserResponse;
import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.auth.entity.RefreshToken;
import com.example.ecommerce.customer.enums.CustomerStatus;
import com.example.ecommerce.user.enums.Role;
import com.example.ecommerce.user.enums.UserStatus;
import com.example.ecommerce.common.exception.AccountLockedException;
import com.example.ecommerce.common.exception.AccountUnverifiedException;
import com.example.ecommerce.common.exception.BadRequestExeption;
import com.example.ecommerce.common.exception.DuplicateResourceException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.common.exception.TokenExpiredException;
import com.example.ecommerce.user.repository.AppUserRepository;
import com.example.ecommerce.customer.repository.CustomerRepository;
import com.example.ecommerce.auth.repository.RefreshTokenRepository;
import com.example.ecommerce.common.util.JwtUtil;
import com.example.ecommerce.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final EmailService emailService;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    // =========================================================================
    // SERVICE METHODS
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail()) || customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email này đã được sử dụng trên hệ thống");
        }
        if (request.getPhone() != null && customerRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Số điện thoại này đã được đăng ký trên hệ thống");
        }

        Customer customer = Customer.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(CustomerStatus.INACTIVE)
                .build();

        AppUser user = AppUser.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .status(UserStatus.UNVERIFIED)
                .customer(customer)
                .failedLoginCount(0)
                .build();

        AppUser savedUser = userRepository.saveAndFlush(user);

        String token = emailService.generateActivationToken();
        emailService.saveActivationToken(token, savedUser.getEmail());
        String activationUrl = backendUrl + "/api/auth/activate-account?token=" + token;
        emailService.sendActivationEmail(savedUser.getEmail(), activationUrl);

        return mapToUserResponse(savedUser);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resendVerificationCode(ResendVerificationRequest request) {
        AppUser user = findUserByEmail(request.getEmail());

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestExeption("Tài khoản đã được kích hoạt trước đó.");
        }
        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.INACTIVE) {
            throw new AccountLockedException("Tài khoản đã bị khóa hoặc ngưng hoạt động.");
        }

        String token = emailService.generateActivationToken();
        emailService.saveActivationToken(token, user.getEmail());
        String activationUrl = backendUrl + "/api/auth/activate-account?token=" + token;
        emailService.sendActivationEmail(user.getEmail(), activationUrl);

        log.info("Đã gửi lại email kích hoạt cho tài khoản {}", user.getEmail());
    }

    @Transactional(rollbackFor = Exception.class)
    public UserResponse activateAccountByToken(String token) {
        String email = emailService.getAndRemoveEmailByToken(token);
        if (email == null) {
            throw new BadRequestExeption("Liên kết xác thực không hợp lệ hoặc đã hết hạn.");
        }

        AppUser user = findUserByEmail(email);
        if (user.getStatus() == UserStatus.ACTIVE) {
            return mapToUserResponse(user);
        }

        user.setStatus(UserStatus.ACTIVE);
        if (user.getCustomer() != null) {
            user.getCustomer().setStatus(CustomerStatus.ACTIVE);
            customerRepository.save(user.getCustomer());
        }

        AppUser savedUser = userRepository.save(user);
        log.info("Kích hoạt tài khoản thành công qua Activation Link cho email {}", email);
        return mapToUserResponse(savedUser);
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestExeption("Email hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailedAttempt(user.getId());
            throw new BadRequestExeption("Email hoặc mật khẩu không chính xác");
        }

        if (user.getStatus() == UserStatus.UNVERIFIED) {
            String token = emailService.generateActivationToken();
            emailService.saveActivationToken(token, user.getEmail());
            String activationUrl = backendUrl + "/api/auth/activate-account?token=" + token;
            emailService.sendActivationEmail(user.getEmail(), activationUrl);

            throw new AccountUnverifiedException(
                    "Tài khoản chưa được kích hoạt. Liên kết kích hoạt mới đã được gửi lại vào email của bạn. Vui lòng kiểm tra hộp thư.");
        }

        if (user.getStatus() == UserStatus.LOCKED
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))) {
            throw new AccountLockedException("Tài khoản đã bị khóa do đăng nhập sai quá nhiều lần");
        }

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new AccountLockedException("Tài khoản đã ngưng hoạt động");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        saveRefreshToken(user, refreshToken);

        return buildAuthResponse(accessToken, refreshToken, mapToUserResponse(user));
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!jwtUtil.validateToken(refreshToken) || !"refresh".equals(jwtUtil.extractType(refreshToken))) {
            throw new BadRequestExeption("Mã làm mới (Refresh token) không hợp lệ");
        }

        String hash = hashToken(refreshToken);
        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestExeption("Mã làm mới đã bị thu hồi hoặc không hợp lệ"));

        if (tokenEntity.isRevoked() || tokenEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Mã làm mới đã hết hạn. Vui lòng đăng nhập lại.");
        }

        AppUser user = tokenEntity.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountLockedException("Tài khoản đã bị khóa hoặc ngừng hoạt động");
        }

        // Token rotation: revoke old token
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);

        // Generate new pair
        String newAccessToken = jwtUtil.generateAccessToken(user);
        String newRefreshToken = jwtUtil.generateRefreshToken(user);

        saveRefreshToken(user, newRefreshToken);

        return buildAuthResponse(newAccessToken, newRefreshToken, mapToUserResponse(user));
    }

    @Transactional(rollbackFor = Exception.class)
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
        return mapToUserResponse(user);
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

    // =========================================================================
    // HELPER METHODS (Hàm dùng chung)
    // =========================================================================

    public AppUser findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với mã ID: " + id));
    }

    public AppUser findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản không tồn tại với email: " + email));
    }

    public UserResponse mapToUserResponse(AppUser user) {
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

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, UserResponse userResponse) {
        return AuthResponse.builder()
                .tokenType("Bearer")
                .accessToken(accessToken)
                .expiresIn(jwtUtil.getAccessTokenTtlSeconds())
                .refreshToken(refreshToken)
                .user(userResponse)
                .build();
    }

    private void saveRefreshToken(AppUser user, String refreshToken) {
        RefreshToken tokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(tokenEntity);
    }
}
