package com.example.ecommerce.dashboard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.dashboard")
public class DashboardProperties {

    /**
     * Timeout cho từng phần dữ liệu trong dashboard (mặc định 1500ms).
     */
    private Duration partTimeout = Duration.ofMillis(1500);

    @Data
    public static class ExecutorConfig {
        private int coreSize = 4;
        private int maxSize = 4;
        private int queueCapacity = 50;
    }
}
