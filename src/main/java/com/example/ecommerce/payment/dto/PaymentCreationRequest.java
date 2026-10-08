package com.example.ecommerce.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreationRequest{
        @NotNull(message = "Mã đơn hàng không được để trống")
        private Integer orderId;

        private String bankCode;
        private String locale;

}
