package com.example.ecommerce.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.lang.reflect.Method;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler(){
        return (Throwable throwable, Method method, Object... params) -> {
            log.error("================ ASYNC ERROR DETECTED ================");
            log.error("Ten ham Async bi loi: {}", method.getName());
            log.error("Tham so truyen vao: {}", params);
            log.error("Chi tiet ngoai le (Exception): ", throwable);
            log.error("======================================================");
        };
    }
}
