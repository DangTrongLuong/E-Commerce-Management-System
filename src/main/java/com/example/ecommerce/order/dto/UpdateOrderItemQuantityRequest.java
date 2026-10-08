package com.example.ecommerce.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderItemQuantityRequest {

    @NotNull(message = "quantity không được để trống")
    @Min(value = 1, message = "quantity phải từ 1 đến 99")
    @Max(value = 99, message = "quantity phải từ 1 đến 99")
    private Integer quantity;
}
