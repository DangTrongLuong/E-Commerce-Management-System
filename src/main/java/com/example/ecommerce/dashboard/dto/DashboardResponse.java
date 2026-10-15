package com.example.ecommerce.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private String date;
    private BigDecimal revenueToday;
    private Map<String, Long> orderCountByStatus;
    private List<TopProductResponse> top5Products;
    private List<LowStockProductResponse> lowStockProducts;
    private boolean partial;
    private List<String> failedParts;
    private long elapsedMs;
}
