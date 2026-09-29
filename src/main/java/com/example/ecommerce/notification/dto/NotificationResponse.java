package com.example.ecommerce.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationResponse {

    private Long id;
    private Long userId;
    private String type;
    private Long ticketId;
    private String message;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
