package com.example.ecommerce.order.dto;

import com.example.ecommerce.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTotalAmountResponse {
    private BigDecimal totalAmount;
    private Long orderCount;
    private LocalDate date;
    private OrderStatus status;
    private String currency;
    private String scope;
    private Long poId;
    private String poEmail;
}
