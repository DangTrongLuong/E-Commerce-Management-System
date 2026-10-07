package com.example.ecommerce.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.redis")
public class RedisProperties {
    private String host;
    private int port;
    private String username;
    private String password;
    private boolean ssl = false;
    private int timeoutMs = 3000;
}
