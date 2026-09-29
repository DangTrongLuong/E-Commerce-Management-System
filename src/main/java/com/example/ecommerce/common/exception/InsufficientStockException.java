package com.example.ecommerce.common.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }

    public static InsufficientStockException of(String productName, int requested, int available) {
        return new InsufficientStockException(
                String.format("Sản phẩm '%s' không đủ hàng: yêu cầu %d, còn lại %d sản phẩm",
                        productName, requested, available));
    }
}
