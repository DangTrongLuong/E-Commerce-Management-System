package com.example.ecommerce.product.repository;

public interface LowStockProductProjection {
    Integer getProductId();
    String getName();
    Integer getStock();
}
