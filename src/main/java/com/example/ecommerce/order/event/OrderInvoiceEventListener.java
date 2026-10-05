package com.example.ecommerce.order.event;

import java.io.File;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.ecommerce.notification.service.EmailService;
import com.example.ecommerce.order.service.PdfInvoiceService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class OrderInvoiceEventListener {
    PdfInvoiceService pdfInvoiceService;
    EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderInvoiceApproved(OrderInvoiceApprovedEvent event) {
        try {
            log.info("Chạy tác vụ sinh PDF & gửi Email bất đồng bộ cho đơn hàng #{}", event.getOrderId());
            byte[] pdfBytes = pdfInvoiceService.generateInvoicePdf(event.getOrderId());
            String filePath = pdfInvoiceService.saveInvoiceToDisk(event.getOrderId(), pdfBytes);
            if (filePath != null) {
                File pdfFile = new File(filePath);
                emailService.sendInvoiceEmailAsync(event.getOrderId(), event.getRecipientEmail(), pdfFile);
            }
        } catch (Exception e) {
            log.warn("Lỗi khi sinh PDF invoice hoặc gửi Mail cho đơn hàng #{}: {}", event.getOrderId(), e.getMessage());
        }
    }

}
