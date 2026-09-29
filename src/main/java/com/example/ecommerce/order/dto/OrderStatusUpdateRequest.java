package com.example.ecommerce.order.dto;

import com.example.ecommerce.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"status"})
    private OrderStatus orderStatus;
}

