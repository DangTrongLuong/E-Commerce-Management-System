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
        // REQ-03: Không sinh PDF và không gửi email kèm file khi duyệt đơn nữa.
        // Email xác nhận thanh toán được chuyển sang quản lý qua EMAIL_OUTBOX khi đơn chuyển PAID.
        log.info("Bỏ qua sinh PDF & gửi Email khi duyệt đơn hàng #{} (chuyển sang luồng EMAIL_OUTBOX)", event.getOrderId());
    }

}
