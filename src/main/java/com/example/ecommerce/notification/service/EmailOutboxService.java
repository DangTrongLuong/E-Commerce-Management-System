package com.example.ecommerce.notification.service;

import com.example.ecommerce.common.config.EMailProperties;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.common.exception.EmailNotResendableException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.notification.dto.EmailOutboxResponse;
import com.example.ecommerce.notification.entity.EmailOutbox;
import com.example.ecommerce.notification.enums.EmailOutboxStatus;
import com.example.ecommerce.notification.repository.EmailOutboxRepository;
import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.entity.OrderItem;
import com.example.ecommerce.order.repository.OrderRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOutboxService {

    private final EmailOutboxRepository emailOutboxRepository;
    private final OrderRepository orderRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EMailProperties eMailProperties;

    @Value("${app.email.backoff-base-seconds:60}")
    private long backoffBaseSeconds;

    @Value("${app.demo.fail-after-paid:false}")
    private boolean failAfterPaid;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static synchronized String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("vi", "VN"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat currencyFormat = new DecimalFormat("#,##0", symbols);
        return currencyFormat.format(amount) + " ₫";
    }

    /**
     * Ghi 1 dòng EMAIL_OUTBOX (TEMPLATE = ORDER_PAID) trong cùng transaction khi đơn hàng chuyển sang PAID.
     */
    @Transactional
    public void createOrderPaidOutbox(Order order) {
        if (order == null || order.getCustomer() == null || order.getCustomer().getEmail() == null) {
            log.warn("Không thể tạo EmailOutbox cho đơn hàng #{} vì thiếu thông tin khách hàng/email",
                    order != null ? order.getId() : null);
            return;
        }

        String recipient = order.getCustomer().getEmail();
        Long referenceId = Long.valueOf(order.getId());

        // Kiểm tra tính Idempotent: nếu đã có bản ghi ORDER_PAID cho order này thì không tạo trùng
        Optional<EmailOutbox> existing = emailOutboxRepository.findByTemplateAndReferenceId("ORDER_PAID", referenceId);
        if (existing.isPresent()) {
            log.info("EmailOutbox cho đơn hàng #{} đã tồn tại (ID #{}), bỏ qua ghi trùng.", order.getId(), existing.get().getId());
            return;
        }

        EmailOutbox outbox = EmailOutbox.builder()
                .template("ORDER_PAID")
                .referenceId(referenceId)
                .recipient(recipient)
                .status(EmailOutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        emailOutboxRepository.save(outbox);
        log.info("Đã ghi EMAIL_OUTBOX (ORDER_PAID) cho đơn hàng #{}, người nhận: {}", order.getId(), recipient);

        // Kịch bản demo: cờ app.demo.fail-after-paid=true ném exception sau khi ghi outbox, trong cùng transaction
        if (failAfterPaid) {
            log.error("[Demo Flag] app.demo.fail-after-paid=true: Kích hoạt ngoại lệ để kiểm tra Transaction Rollback");
            throw new RuntimeException("Demo fail-after-paid exception: kiểm tra Transaction Rollback");
        }
    }

    /**
     * Xử lý 1 dòng outbox gửi email.
     */
    @Transactional
    public void processOutboxItem(EmailOutbox outbox) {
        try {
            if ("ORDER_PAID".equalsIgnoreCase(outbox.getTemplate())) {
                sendOrderPaidEmail(outbox);
            } else {
                log.warn("Không hỗ trợ template: {}", outbox.getTemplate());
            }

            outbox.setStatus(EmailOutboxStatus.SENT);
            outbox.setSentAt(LocalDateTime.now());
            outbox.setLastError(null);
            emailOutboxRepository.save(outbox);
            log.info("Gửi email thành công cho outbox ID #{}, người nhận: {}", outbox.getId(), outbox.getRecipient());
        } catch (Exception e) {
            int attempts = (outbox.getAttempts() != null ? outbox.getAttempts() : 0) + 1;
            outbox.setAttempts(attempts);

            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (errorMsg.length() > 1000) {
                errorMsg = errorMsg.substring(0, 1000);
            }
            outbox.setLastError(errorMsg);

            if (attempts >= 5) {
                outbox.setStatus(EmailOutboxStatus.FAILED);
                log.error("Outbox ID #{} thất bại lần thứ {} -> Chuyển FAILED. Lý do: {}", outbox.getId(), attempts, errorMsg);
            } else {
                long delaySeconds = backoffBaseSeconds * (1L << (attempts - 1));
                outbox.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
                log.warn("Outbox ID #{} gửi lỗi lần {}. Thử lại sau {} giây lúc {}. Lý do: {}",
                        outbox.getId(), attempts, delaySeconds, outbox.getNextAttemptAt(), errorMsg);
            }
            emailOutboxRepository.save(outbox);
        }
    }

    private void sendOrderPaidEmail(EmailOutbox outbox) throws Exception {
        Order order = orderRepository.findById(outbox.getReferenceId().intValue())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + outbox.getReferenceId()));

        String customerName = order.getCustomer() != null ? order.getCustomer().getName() : "Quý khách";
        LocalDateTime paidAt = order.getPaidAt() != null ? order.getPaidAt() : LocalDateTime.now();
        String formattedPaidAt = paidAt.format(DATE_TIME_FORMATTER);

        List<Map<String, Object>> itemsList = new ArrayList<>();
        List<OrderItem> allItems = new ArrayList<>();
        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            allItems.addAll(order.getOrderItems());
        } else if (order.getSubOrders() != null) {
            for (com.example.ecommerce.order.entity.SubOrder sub : order.getSubOrders()) {
                if (sub.getOrderItems() != null) {
                    allItems.addAll(sub.getOrderItems());
                }
            }
        }

        for (OrderItem item : allItems) {
            Map<String, Object> itemMap = new HashMap<>();
            String pName = item.getProduct() != null ? item.getProduct().getName() : "Sản phẩm";
            BigDecimal uPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal sTotal = item.getSubTotal() != null ? item.getSubTotal() : uPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

            itemMap.put("productName", pName);
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("unitPriceFormatted", formatCurrency(uPrice));
            itemMap.put("subtotalFormatted", formatCurrency(sTotal));
            itemsList.add(itemMap);
        }

        String formattedTotal = formatCurrency(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);

        Context context = new Context();
        context.setVariable("customerName", customerName);
        context.setVariable("orderId", order.getId());
        context.setVariable("paidAt", formattedPaidAt);
        context.setVariable("items", itemsList);
        context.setVariable("totalAmountFormatted", formattedTotal);

        String htmlContent = templateEngine.process("order-paid-email", context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        String fromEmail = eMailProperties.getUsername();
        if (fromEmail != null && !fromEmail.isBlank()) {
            helper.setFrom(fromEmail);
        }
        helper.setTo(outbox.getRecipient());
        helper.setSubject("[Shop] Xác nhận thanh toán đơn hàng #" + order.getId());
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmailOutboxResponse> getEmails(EmailOutboxStatus status, Pageable pageable) {
        Page<EmailOutbox> page;
        if (status != null) {
            page = emailOutboxRepository.findByStatus(status, pageable);
        } else {
            page = emailOutboxRepository.findAll(pageable);
        }
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional
    public EmailOutboxResponse resendEmail(Long id) {
        EmailOutbox outbox = emailOutboxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy email outbox với mã ID: " + id));

        if (outbox.getStatus() != EmailOutboxStatus.FAILED) {
            throw new EmailNotResendableException("Chỉ có thể gửi lại email ở trạng thái FAILED. Trạng thái hiện tại: " + outbox.getStatus());
        }

        outbox.setStatus(EmailOutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(LocalDateTime.now());
        EmailOutbox saved = emailOutboxRepository.save(outbox);
        log.info("Email outbox #{} đã được đặt lại PENDING để gửi lại", id);
        return toResponse(saved);
    }

    private EmailOutboxResponse toResponse(EmailOutbox outbox) {
        return EmailOutboxResponse.builder()
                .id(outbox.getId())
                .template(outbox.getTemplate())
                .referenceId(outbox.getReferenceId())
                .recipient(outbox.getRecipient())
                .status(outbox.getStatus())
                .attempts(outbox.getAttempts())
                .nextAttemptAt(outbox.getNextAttemptAt())
                .lastError(outbox.getLastError())
                .createdAt(outbox.getCreatedAt())
                .sentAt(outbox.getSentAt())
                .build();
    }
}
