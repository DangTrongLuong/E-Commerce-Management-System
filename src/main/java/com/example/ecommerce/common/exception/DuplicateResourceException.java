package com.example.ecommerce.common.exception;

public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }

    public static DuplicateResourceException of(String resourceName, String fieldName, Object fieldValue){
        return new DuplicateResourceException(
                String.format("%s đã tồn tại với %s: %s", resourceName, fieldName, fieldValue)
        );
    }
}
