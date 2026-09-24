package com.example.ecommerce.dto.response;

import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.enums.PaymentStatus;
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
public class PaymentResponse {
    private Long id;
    private Integer orderId;
    private String vnpTxnRef;
    private String vnpTransactionNo;
    private BigDecimal amount;
    private String bankCode;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String responseCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
