package com.example.ecommerce.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.ecommerce.common.config.EMailProperties;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service("authEmailService")
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final EMailProperties eMailProperties;
    private final Map<String, String> memoryOtpStore = new ConcurrentHashMap<>();
    private static final String OTP_PREFIX = "email_verify_code:";
    private static final long OTP_EXPIRE_MINUTES = 10;

    public String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    public void saveVerificationCode(String email, String code) {
        try {
            redisTemplate.opsForValue().set(OTP_PREFIX + email, code, OTP_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Lỗi lưu OTP vào Redis, dùng memory fallback: {}", e.getMessage());
            memoryOtpStore.put(email, code);
        }
    }

    public boolean verifyCode(String email, String inputCode) {
        String storedCode = null;
        try {
            storedCode = redisTemplate.opsForValue().get(OTP_PREFIX + email);
        } catch (Exception e) {
            log.warn("Lỗi đọc OTP từ Redis, dùng memory fallback: {}", e.getMessage());
            storedCode = memoryOtpStore.get(email);
        }
        if (storedCode != null && storedCode.equals(inputCode)) {
            deleteVerificationCode(email);
            return true;
        }
        return false;
    }

    public void deleteVerificationCode(String email) {
        try {
            redisTemplate.delete(OTP_PREFIX + email);
        } catch (Exception ignored) {
        }
        memoryOtpStore.remove(email);
    }

    @Async
    public void sendVerificationEmail(String toEmail, String code) {
        try {
            String fromEmail = eMailProperties.getUsername();
            SimpleMailMessage message = new SimpleMailMessage();
            if (fromEmail != null && !fromEmail.isBlank()) {
                message.setFrom(fromEmail);
            }
            message.setTo(toEmail);
            message.setSubject("[Shop] Mã xác thực kích hoạt tài khoản");
            message.setText("Xin chào,\n\nMã xác thực đăng ký tài khoản của bạn là: " + code
                    + "\nMã có hiệu lực trong " + OTP_EXPIRE_MINUTES
                    + " phút.\n\nVui lòng nhập mã này trên ứng dụng để kích hoạt tài khoản.");
            mailSender.send(message);
            log.info("Đã gửi mã xác thực {} từ {} tới email {}", code, fromEmail, toEmail);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email xác thực tới {}: ", toEmail, e);
        }
    }
}
