package com.example.ecommerce.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Valid
@ConfigurationProperties(prefix = "vnpay")
public class VnPayProperties {
    @NotBlank(message = "Vui lòng cung cấp thông tin tmnCode !")
    private String tmnCode;

    @NotBlank(message = "Vui lòng cung cấp thông tin hashSecret !")
    private String hashSecret;

    @NotBlank(message = "Vui lòng cung cấp thông tin payUrl !")
    private String payUrl;

    @NotBlank(message = "Vui lòng cung cấp thông tin returnUrl")
    private String returnUrl;

    private String apiVersion = "2.1.0";

    private String command = "pay";

    private String orderType = "other";

    private String currencyCode = "VND";

    private String defaultLocale = "vn";

    private int expireMinutes = 15;

}
