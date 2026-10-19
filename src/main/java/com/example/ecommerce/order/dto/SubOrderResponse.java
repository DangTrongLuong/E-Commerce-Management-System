package com.example.ecommerce.order.dto;

import com.example.ecommerce.order.enums.OrderPaymentStatus;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.ticket.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubOrderResponse {
    private Long id;
    private Integer orderId;
    private String subOrderCode;
    private Long poId;
    private String poName;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private OrderPaymentStatus paymentStatus;
    private List<OrderItemResponse> items;
    private Long ticketId;
    private TicketStatus ticketStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
