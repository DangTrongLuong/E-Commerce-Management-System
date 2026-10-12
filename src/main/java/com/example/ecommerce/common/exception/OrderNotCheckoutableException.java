package com.example.ecommerce.common.exception;

public class OrderNotCheckoutableException extends RuntimeException {
    public OrderNotCheckoutableException(String message) {
        super(message);
    }
}
