package com.example.ecommerce.auth.service;

import com.example.ecommerce.common.exception.BadRequestExeption;

import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.user.enums.UserStatus;
import com.example.ecommerce.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final AppUserRepository userRepository;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            int newCount = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(newCount);
            if (newCount >= MAX_FAILED_ATTEMPTS) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
                log.warn("User '{}' locked after {} failed login attempts. Locked until: {}",
                        user.getEmail(), newCount, user.getLockedUntil());
            } else {
                log.debug("User '{}' failed login attempt {}/{}", user.getEmail(), newCount, MAX_FAILED_ATTEMPTS);
            }
            userRepository.save(user);
        });
    }
}
