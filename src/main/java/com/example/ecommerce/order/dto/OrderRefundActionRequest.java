package com.example.ecommerce.order.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRefundActionRequest {
    private String comment;
}
