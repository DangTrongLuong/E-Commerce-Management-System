package com.example.ecommerce.dashboard.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class DashboardAsyncConfig {

    @Value("${app.executors.dashboard.core-size:4}")
    private int coreSize;

    @Value("${app.executors.dashboard.max-size:4}")
    private int maxSize;

    @Value("${app.executors.dashboard.queue-capacity:50}")
    private int queueCapacity;

    @Bean(name = "dashboardExecutor")
    public ThreadPoolTaskExecutor dashboardExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("dashboard-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        // Truyền MDC (bao gồm traceId) từ request thread sang worker thread
        executor.setTaskDecorator(runnable -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        });

        executor.initialize();
        return executor;
    }
}
