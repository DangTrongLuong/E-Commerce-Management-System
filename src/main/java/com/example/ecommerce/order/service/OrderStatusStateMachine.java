package com.example.ecommerce.order.service;

import com.example.ecommerce.common.exception.ConflictException;
import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.entity.OrderStatusHistory;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.example.ecommerce.order.enums.OrderPaymentStatus;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusStateMachine {

    private final OrderStatusHistoryRepository historyRepository;
    private final com.example.ecommerce.notification.service.EmailOutboxService emailOutboxService;
    private final com.example.ecommerce.order.repository.SubOrderRepository subOrderRepository;
    private final com.example.ecommerce.order.repository.OrderRepository orderRepository;

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
            order.setPaymentStatus(OrderPaymentStatus.PAID);
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

        // REQ-03: Khi đơn hàng chuyển PAID, ghi EMAIL_OUTBOX (TEMPLATE = ORDER_PAID) trong cùng transaction
        if (newStatus == OrderStatus.PAID) {
            emailOutboxService.createOrderPaidOutbox(order);
        }

        // CASCADE DOWNHILL: Nếu đơn CHA chuyển PAID hoặc CANCELLED -> Tự động chuyển các đơn CON (SubOrder) tương ứng
        if (order != null) {
            List<com.example.ecommerce.order.entity.SubOrder> subOrders = subOrderRepository.findByOrderId(order.getId());
            if (subOrders != null && !subOrders.isEmpty()) {
                if (newStatus == OrderStatus.PAID) {
                    for (com.example.ecommerce.order.entity.SubOrder sub : subOrders) {
                        if (sub.getStatus() == OrderStatus.PENDING || sub.getStatus() == OrderStatus.AWAITING_PAYMENT) {
                            sub.setStatus(OrderStatus.PAID);
                            sub.setPaymentStatus(OrderPaymentStatus.PAID);
                            subOrderRepository.save(sub);
                        }
                    }
                } else if (newStatus == OrderStatus.CANCELLED) {
                    for (com.example.ecommerce.order.entity.SubOrder sub : subOrders) {
                        if (sub.getStatus() == OrderStatus.PENDING || sub.getStatus() == OrderStatus.AWAITING_PAYMENT) {
                            sub.setStatus(OrderStatus.CANCELLED);
                            subOrderRepository.save(sub);
                        }
                    }
                }
            }
        }
    }

    public void transitionSubOrder(com.example.ecommerce.order.entity.SubOrder subOrder, OrderStatus newStatus, String changedBy, String reason) {
        OrderStatus oldStatus = subOrder.getStatus();
        if (oldStatus == newStatus) return;

        subOrder.setStatus(newStatus);
        if (newStatus == OrderStatus.PAID) {
            subOrder.setPaymentStatus(OrderPaymentStatus.PAID);
        }
        subOrderRepository.save(subOrder);

        log.info("SubOrder {} transitioned: {} -> {} by [{}]", subOrder.getSubOrderCode(), oldStatus, newStatus, changedBy);

        // UPHILL CHECK: Nếu tất cả SubOrder của Order đều đã PAID hoặc CANCELLED -> Chuyển Order sang PAID
        if (newStatus == OrderStatus.PAID && subOrder.getOrder() != null) {
            Integer parentId = subOrder.getOrder().getId();
            List<com.example.ecommerce.order.entity.SubOrder> allSubOrders = subOrderRepository.findByOrderId(parentId);
            if (allSubOrders != null && !allSubOrders.isEmpty()) {
                boolean allFinished = allSubOrders.stream()
                        .allMatch(s -> s.getId().equals(subOrder.getId()) || s.getStatus() == OrderStatus.PAID || s.getStatus() == OrderStatus.CANCELLED);
                boolean hasPaid = allSubOrders.stream()
                        .anyMatch(s -> s.getId().equals(subOrder.getId()) || s.getStatus() == OrderStatus.PAID);
                if (allFinished && hasPaid) {
                    orderRepository.findById(parentId).ifPresent(parent -> {
                        if (parent.getStatus() != OrderStatus.PAID) {
                            transition(parent, OrderStatus.PAID, changedBy, "All sub-orders paid");
                            orderRepository.save(parent);
                        }
                    });
                }
            }
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
