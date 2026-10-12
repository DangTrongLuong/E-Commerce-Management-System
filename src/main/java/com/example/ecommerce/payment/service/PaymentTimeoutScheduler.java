package com.example.ecommerce.payment.service;

import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.entity.OrderItem;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.order.service.OrderStatusStateMachine;
import com.example.ecommerce.payment.entity.PaymentTransaction;
import com.example.ecommerce.payment.enums.PaymentStatus;
import com.example.ecommerce.payment.repository.PaymentTransactionRepository;
import com.example.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ProductRepository productRepository;
    private final OrderStatusStateMachine stateMachine;

    @Value("${app.payment.timeout-minutes:15}")
    private int timeoutMinutes;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void autoCancelExpiredAwaitingPaymentOrders() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<Order> expiredOrders = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.AWAITING_PAYMENT,
                cutoffTime);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("[PaymentTimeoutScheduler] Tìm thấy {} đơn hàng AWAITING_PAYMENT quá hạn timeout ({} phút)",
                expiredOrders.size(), timeoutMinutes);

        for (Order orderCandidate : expiredOrders) {
            try {
                // Khóa bi quan và đọc lại trạng thái mới nhất để chống race condition với
                // Callback
                Order order = orderRepository.findByIdForUpdate(orderCandidate.getId()).orElse(null);
                if (order == null || order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
                    log.info("[PaymentTimeoutScheduler] Đơn hàng #{} không còn ở AWAITING_PAYMENT, bỏ qua hủy tự động",
                            orderCandidate.getId());
                    continue;
                }

                log.info("[PaymentTimeoutScheduler] Tự động hủy đơn hàng quá hạn #{}", order.getId());
                stateMachine.transition(order, OrderStatus.CANCELLED, "SYSTEM", "PAYMENT_TIMEOUT");

                // Cập nhật các giao dịch thanh toán PENDING sang EXPIRED
                List<PaymentTransaction> pendingPayments = paymentTransactionRepository
                        .findByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING);
                for (PaymentTransaction pt : pendingPayments) {
                    pt.setStatus(PaymentStatus.EXPIRED);
                    paymentTransactionRepository.save(pt);
                    log.info("Cập nhật PaymentTransaction #{} sang EXPIRED cho đơn hàng #{}", pt.getId(),
                            order.getId());
                }

                // Hoàn lại tồn kho cho từng sản phẩm trong đơn hàng nguyên tử
                for (OrderItem item : order.getOrderItems()) {
                    if (item.getProduct() != null) {
                        productRepository.increaseStockAtomic(item.getProduct().getId(), item.getQuantity());
                        log.info("Hoàn tồn kho sản phẩm ID {}: +{}", item.getProduct().getId(), item.getQuantity());
                    }
                }

                orderRepository.save(order);
            } catch (Exception e) {
                log.error("[PaymentTimeoutScheduler] Thất bại khi tự động hủy đơn hàng quá hạn #{}",
                        orderCandidate.getId(), e);
            }
        }
    }
}
