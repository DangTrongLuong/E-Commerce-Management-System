package com.example.ecommerce.order.dto;

import com.example.ecommerce.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCheckoutResponse {
    private Integer orderId;
    private Long subOrderId;
    private String subOrderCode;
    private OrderStatus status;
    private Long paymentId;
    private BigDecimal amount;
    private String paymentUrl;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;
}
