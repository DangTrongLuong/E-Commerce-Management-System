package com.example.ecommerce.exception;

public class SelfApprovalNotAllowedException extends RuntimeException {
    public SelfApprovalNotAllowedException(String message) {
        super(message);
    }
}
