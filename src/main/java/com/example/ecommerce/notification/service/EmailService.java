package com.example.ecommerce.notification.service;

import com.example.ecommerce.common.config.EMailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EMailProperties eMailProperties;

    @Async
    public void sendInvoiceEmailAsync(Integer orderId, String toEmail, File pdfAttachment) {
        String fromEmail = eMailProperties.getUsername();
        String targetEmail = eMailProperties.getTargetEmail();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(targetEmail);
            helper.setSubject("Hóa đơn đơn hàng #" + orderId + " - Xác nhận thành công");
            helper.setText("Kính gửi Quý khách,\n\nĐơn hàng #" + orderId + " của bạn đã được phê duyệt thành công.\nChi tiết hóa đơn được đính kèm trong file PDF bên dưới.\n\nTrân trọng,", false);

            if (pdfAttachment != null && pdfAttachment.exists()) {
                helper.addAttachment("Invoice_Order_" + orderId + ".pdf", pdfAttachment);
            }

            mailSender.send(message);
            log.info("Đã gửi email hóa đơn đơn hàng #{} thành công tới {}", orderId, targetEmail);
        } catch (MessagingException e) {
            log.error("Lỗi khi gửi email hóa đơn đơn hàng #{} tới {}: {}", orderId, targetEmail, e.getMessage());
        }
    }
}
