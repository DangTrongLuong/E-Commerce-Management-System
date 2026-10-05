package com.example.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRefundRequest {
    @NotBlank(message = "Reason is required for refund request")
    private String reason;
}
