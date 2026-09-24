package com.example.ecommerce.service;

import com.example.ecommerce.entity.AppUser;
import com.example.ecommerce.enums.UserStatus;
import com.example.ecommerce.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Handles failed login attempt tracking in an isolated transaction.
 *
 * This must be a SEPARATE Spring bean (not a private method of AuthService)
 * so that Spring's AOP proxy intercepts the @Transactional(REQUIRES_NEW) call.
 * Self-invocation within the same bean bypasses AOP proxies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final AppUserRepository userRepository;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    /**
     * Increments the failed login counter for the given user ID and locks the account
     * if the threshold has been reached.
     *
     * Uses REQUIRES_NEW propagation so this update is ALWAYS committed to the DB,
     * even if the caller's transaction rolls back (e.g. because AuthService.login()
     * throws BadRequestExeption after calling this method).
     */
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
