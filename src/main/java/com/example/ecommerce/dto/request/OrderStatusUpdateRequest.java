package com.example.ecommerce.dto.request;

import com.example.ecommerce.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdateRequest {
    @NotNull(message = "Trạng thái đơn hàng không thể để trống")
    private OrderStatus orderStatus;
}
