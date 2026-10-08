package com.example.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRefundRequest {
    @NotBlank(message = "Vui lòng nhập lý do yêu cầu hoàn tiền")
    private String reason;
}
