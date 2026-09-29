package com.example.ecommerce.common.exception;

import com.example.ecommerce.order.entity.Order;

public class InvalidOrderStatusException extends RuntimeException {
    public InvalidOrderStatusException(String message) {
        super(message);
    }

    public static InvalidOrderStatusException of(String from, String to) {
        return new InvalidOrderStatusException(
                String.format("Không thể chuyển trạng thái Order từ %s sang %s", from, to));
    }
}
