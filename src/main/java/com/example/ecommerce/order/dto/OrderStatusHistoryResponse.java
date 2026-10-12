package com.example.ecommerce.order.dto;

import com.example.ecommerce.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusHistoryResponse {
    private Long id;
    private Integer orderId;
    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private String changedBy;
    private String reason;
    private LocalDateTime createdAt;
}
