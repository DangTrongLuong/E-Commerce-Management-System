package com.example.ecommerce.exception;

public class MixedOwnerOrderException extends RuntimeException {
    public MixedOwnerOrderException(String message) {
        super(message);
    }
}
