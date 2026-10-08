package com.example.ecommerce.notification.service;

import com.example.ecommerce.common.config.EMailProperties;
import com.example.ecommerce.notification.entity.Notification;
import com.example.ecommerce.notification.repository.NotificationRepository;
import com.example.ecommerce.user.repository.AppUserRepository;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service("notificationEmailService")
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EMailProperties eMailProperties;
    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;

    @Async
    @Retryable(retryFor = { MessagingException.class, MailException.class,
            RuntimeException.class }, maxAttempts = 3, backoff = @Backoff(delay = 5000))
    public void sendInvoiceEmailAsync(Integer orderId, String toEmail, File pdfAttachment) throws Exception {
        String fromEmail = eMailProperties.getUsername();
        String targetEmail = eMailProperties.getTargetEmail();

        log.info("Đang gửi email hóa đơn đơn hàng #{} tới {}", orderId, targetEmail);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(targetEmail);
        helper.setSubject("Hóa đơn đơn hàng #" + orderId + " - Xác nhận thành công");
        helper.setText("Kính gửi Quý khách,\n\nĐơn hàng #" + orderId
                + " của bạn đã được phê duyệt thành công.\nChi tiết hóa đơn được đính kèm trong file PDF bên dưới.\n\nTrân trọng,",
                false);

        if (pdfAttachment != null && pdfAttachment.exists()) {
            helper.addAttachment("Invoice_Order_" + orderId + ".pdf", pdfAttachment);
        }

        mailSender.send(message);
        log.info("Đã gửi email hóa đơn đơn hàng #{} thành công tới {}", orderId, targetEmail);

    }

    @Recover
    public void handleEmailFailure(Exception e, Integer orderId, String toEmail, File pdfAttachment) {
        log.error("Gửi email hóa đơn đơn hàng #{}\" + \" thất bại sau 3 lần thử. Lý do: {}", orderId, e.getMessage());

        if (toEmail != null) {
            appUserRepository.findByEmail(toEmail).ifPresent(user -> {
                Notification fallbackNotification = Notification.builder()
                        .user(user)
                        .type("EMAIL_SEND_FAILED")
                        .message("Hệ thống không thể gửi email hóa đơn cho đơn hàng #" + orderId +
                                ". Quý khách vui lòng tải trực tiếp file PDF hóa đơn tại trang chi tiết đơn hàng.")
                        .build();

                notificationRepository.save(fallbackNotification);
                log.info("Đã tạo In-App Notification báo lỗi gửi Mail cho User {}", user.getEmail());
            });
        }
    }
}
