package com.example.ecommerce.common.exception;

public class IdempotencyKeyRequiredException extends RuntimeException {
    public IdempotencyKeyRequiredException(String message) {
        super(message);
    }
}
