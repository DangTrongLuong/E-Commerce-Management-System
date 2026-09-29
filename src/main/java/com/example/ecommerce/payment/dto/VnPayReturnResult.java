package com.example.ecommerce.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VnPayReturnResult {
    private Integer orderId;
    private String vnpTxnRef;
    private String responseCode;
    private String message;
    private BigDecimal amount;
}
