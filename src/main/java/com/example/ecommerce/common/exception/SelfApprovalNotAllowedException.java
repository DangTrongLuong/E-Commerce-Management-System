package com.example.ecommerce.common.exception;

public class SelfApprovalNotAllowedException extends RuntimeException {
    public SelfApprovalNotAllowedException(String message) {
        super(message);
    }
}
