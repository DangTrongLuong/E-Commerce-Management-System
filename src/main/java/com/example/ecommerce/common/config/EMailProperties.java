package com.example.ecommerce.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "spring.mail")
public class EMailProperties {
    private String username;
    private String mailReceiver;

    public String getTargetEmail(){
        if(mailReceiver != null && !mailReceiver.isBlank()){
            return mailReceiver;
        }
        return username;
    }
}
