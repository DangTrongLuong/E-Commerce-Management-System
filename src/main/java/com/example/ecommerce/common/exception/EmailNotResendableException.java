package com.example.ecommerce.common.exception;

public class EmailNotResendableException extends RuntimeException {
    public EmailNotResendableException(String message) {
        super(message);
    }
}
