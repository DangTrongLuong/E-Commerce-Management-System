package com.example.ecommerce.common.exception;

public class BadRequestExeption extends RuntimeException {
    public BadRequestExeption(String message) {
        super(message);
    }
}
