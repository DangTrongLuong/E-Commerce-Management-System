package com.example.ecommerce.order.port;

public interface OrderPaymentGateway {
    OrderPaymentInfo getOrderForPayment(Long orderId);

    void markOrderAsPaid(Long orderId, Long paymentTransactionId);

    void markOrderPaymentFailed(Long orderId, Long paymentTransactionId);
}
