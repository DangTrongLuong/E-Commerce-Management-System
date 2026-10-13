package com.example.ecommerce.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {

    private Long id;
    private Integer productId;
    private String productName;
    private String imageUrl;
    private BigDecimal price;
    private Integer quantity;
    private Integer stock;
    private BigDecimal subtotal;
}
