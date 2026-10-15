package com.example.ecommerce.dashboard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.demo.dashboard")
public class DashboardDemoProperties {
    /**
     * Cấu hình độ trễ giả lập theo tên phần (ví dụ: top5Products: 3s).
     */
    private Map<String, Duration> delay = new HashMap<>();

    /**
     * Danh sách tên các phần cần giả lập lỗi ném exception (ví dụ: [lowStockProducts]).
     */
    private List<String> fail = new ArrayList<>();
}
