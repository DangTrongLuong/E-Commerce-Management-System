package com.example.ecommerce.common.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CloudinaryConfig {

    private final CloudinaryProperties cloudinaryProperties;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudinaryProperties.getCloudName() != null ? cloudinaryProperties.getCloudName() : "dummy",
                "api_key", cloudinaryProperties.getApiKey() != null ? cloudinaryProperties.getApiKey() : "dummy",
                "api_secret", cloudinaryProperties.getApiSecret() != null ? cloudinaryProperties.getApiSecret() : "dummy",
                "secure", true
        ));
    }
}
