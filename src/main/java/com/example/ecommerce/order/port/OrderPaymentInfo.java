package com.example.ecommerce.order.port;

import java.math.BigDecimal;

public record OrderPaymentInfo(
        Long orderId,
        BigDecimal totalAmount,
        String currentStatus
) {
}
