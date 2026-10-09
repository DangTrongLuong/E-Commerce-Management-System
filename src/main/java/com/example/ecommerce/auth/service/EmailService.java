package com.example.ecommerce.auth.service;

import com.example.ecommerce.common.config.EMailProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service("authEmailService")
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final EMailProperties eMailProperties;
    private final Map<String, String> memoryTokenStore = new ConcurrentHashMap<>();

    private static final String TOKEN_PREFIX = "email_activation_token:";
    private static final long EXPIRE_MINUTES = 10;

    public String generateActivationToken() {
        return UUID.randomUUID().toString();
    }

    public void saveActivationToken(String token, String email) {
        try {
            redisTemplate.opsForValue().set(TOKEN_PREFIX + token, email, EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Lỗi lưu Token kích hoạt vào Redis, dùng memory fallback: {}", e.getMessage());
            memoryTokenStore.put(token, email);
        }
    }

    public String getAndRemoveEmailByToken(String token) {
        String email = null;
        try {
            email = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
            if (email != null) {
                redisTemplate.delete(TOKEN_PREFIX + token);
            }
        } catch (Exception e) {
            log.warn("Lỗi đọc Token từ Redis, dùng memory fallback: {}", e.getMessage());
            email = memoryTokenStore.remove(token);
        }
        return email;
    }

    @Async
    public void sendActivationEmail(String toEmail, String activationUrl) {
        try {
            String fromEmail = eMailProperties.getUsername();
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            if (fromEmail != null && !fromEmail.isBlank()) {
                helper.setFrom(fromEmail);
            }
            helper.setTo(toEmail);
            helper.setSubject("Xác thực tài khoản E-Commerce");

            ClassPathResource resource = new ClassPathResource("templates/activation-email.html");
            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String htmlContent = String.format(template, EXPIRE_MINUTES, activationUrl);

            helper.setText(htmlContent, true);
            mailSender.send(mimeMessage);
            log.info("Đã gửi email kích hoạt HTML tới email {}", toEmail);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email kích hoạt HTML tới {}: ", toEmail, e);
        }
    }
}

