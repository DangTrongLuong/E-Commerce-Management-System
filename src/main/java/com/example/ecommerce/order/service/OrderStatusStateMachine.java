package com.example.ecommerce.order.service;

import com.example.ecommerce.common.exception.ConflictException;
import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.entity.OrderStatusHistory;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusStateMachine {

    private final OrderStatusHistoryRepository historyRepository;
    private final com.example.ecommerce.notification.service.EmailOutboxService emailOutboxService;

    public void transition(Order order, OrderStatus newStatus, String changedBy, String reason) {
        OrderStatus oldStatus = order.getStatus();

        if (oldStatus == newStatus) {
            log.info("Order {} status already {}, skipping transition & history recording", order.getId(), oldStatus);
            return;
        }

        if (!isValidTransition(oldStatus, newStatus)) {
            log.error("Invalid status transition for Order {}: {} -> {}", order.getId(), oldStatus, newStatus);
            throw new ConflictException("Không thể chuyển trạng thái đơn hàng từ " + oldStatus + " sang " + newStatus);
        }

        order.setStatus(newStatus);
        if (newStatus == OrderStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
        }
        if (newStatus == OrderStatus.CANCELLED && reason != null) {
            order.setCancelReason(reason);
        }

        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(oldStatus)
                .toStatus(newStatus)
                .changedBy(changedBy)
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build();

        historyRepository.save(history);
        log.info("Order {} transitioned: {} -> {} by [{}]", order.getId(), oldStatus, newStatus, changedBy);

        // REQ-03: Khi đơn hàng chuyển PAID, hệ thống PHẢI ghi 1 dòng EMAIL_OUTBOX (TEMPLATE = ORDER_PAID) trong cùng transaction
        if (newStatus == OrderStatus.PAID) {
            emailOutboxService.createOrderPaidOutbox(order);
        }
    }

    public boolean isValidTransition(OrderStatus from, OrderStatus to) {
        if (from == to) return true;

        switch (from) {
            case PENDING:
                return to == OrderStatus.AWAITING_PAYMENT || to == OrderStatus.CANCELLED;
            case AWAITING_PAYMENT:
                return to == OrderStatus.PAID || to == OrderStatus.CANCELLED;
            case PAID:
                return to == OrderStatus.CONFIRMED || to == OrderStatus.CANCELLED;
            case CONFIRMED:
                return to == OrderStatus.PROCESSING || to == OrderStatus.COMPLETED || to == OrderStatus.CANCELLED;
            case PROCESSING:
                return to == OrderStatus.COMPLETED || to == OrderStatus.CANCELLED || to == OrderStatus.REFUND_REQUESTED;
            case COMPLETED:
                return to == OrderStatus.REFUND_REQUESTED;
            case REFUND_REQUESTED:
                return to == OrderStatus.REFUNDED || to == OrderStatus.PROCESSING || to == OrderStatus.COMPLETED;
            default:
                return false;
        }
    }
}
