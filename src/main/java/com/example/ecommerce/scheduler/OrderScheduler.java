package com.example.ecommerce.scheduler;

import com.example.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderScheduler {
    private final OrderService orderService;

    @Scheduled(cron = "0 0 0 * * *")
    public void autoCancelExpiredPendingOrders() {
        log.info("[OrderScheduler] Bắt đầu job kiểm tra đơn hàng PENDING quá 24h...");
        try {
            orderService.cancelExpiredPendingOrders();
        } catch (Exception e) {
            log.error("[OrderScheduler] Lỗi khi chạy job hủy đơn hàng PENDING quá hạn", e);
        }
        log.info("[OrderScheduler] Kết thúc job kiểm tra đơn hàng PENDING quá 24h");
    }
}
