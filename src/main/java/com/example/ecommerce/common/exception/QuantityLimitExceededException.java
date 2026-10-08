package com.example.ecommerce.common.exception;

public class QuantityLimitExceededException extends RuntimeException {
    public QuantityLimitExceededException(String message) {
        super(message);
    }
}