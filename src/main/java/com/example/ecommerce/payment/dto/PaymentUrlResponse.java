package com.example.ecommerce.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentUrlResponse {
    private String paymentUrl;
    private String txnRef;
    private BigDecimal amount;
    private LocalDateTime expireAt;
}
