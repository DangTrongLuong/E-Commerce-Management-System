package com.example.ecommerce.notification.dto;

import com.example.ecommerce.notification.enums.EmailOutboxStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailOutboxResponse {
    private Long id;
    private String template;
    private Long referenceId;
    private String recipient;
    private EmailOutboxStatus status;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
