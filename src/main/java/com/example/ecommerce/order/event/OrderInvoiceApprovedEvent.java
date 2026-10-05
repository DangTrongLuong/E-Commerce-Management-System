package com.example.ecommerce.order.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderInvoiceApprovedEvent {
    private final Integer orderId;
    private final String recipientEmail;
}
