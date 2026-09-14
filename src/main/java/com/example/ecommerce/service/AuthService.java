package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.LoginRequest;
import com.example.ecommerce.dto.request.RegisterRequest;
import com.example.ecommerce.dto.response.AuthResponse;
import com.example.ecommerce.dto.response.UserResponse;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.enums.CustomerStatus;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.exception.BadRequestExeption;
import com.example.ecommerce.exception.DuplicateResourceException;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.util.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    UserRepository userRepository;
    CustomerRepository customerRepository;
    PasswordEncoder passwordEncoder;
    JwtUtil jwtUtil;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail()) || customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email đã được sử dụng!");
        }

        Customer customer = Customer.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(CustomerStatus.ACTIVE)
                .build();
        Customer savedCustomer = customerRepository.save(customer);

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .tokenVersion(1)
                .status("ACTIVE")
                .customer(savedCustomer)
                .build();
        User savedUser = userRepository.save(user);

        return UserResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .customerId(savedCustomer.getId())
                .createdAt(savedUser.getCreatedAt())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestExeption("Email hoặc mật khẩu không chính xác!"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadRequestExeption("Email hoặc mật khẩu không chính xác!");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BadRequestExeption("Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động!");
        }

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        Integer customerId = user.getCustomer() != null ? user.getCustomer().getId() : null;

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .customerId(customerId)
                .build();
    }

    @Transactional
    public void logout(User currentUser) {
        if (currentUser == null) {
            throw new BadRequestExeption("Bạn chưa đăng nhập!");
        }
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new BadRequestExeption("Không tìm thấy thông tin người dùng!"));

        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }
}
