package com.example.ecommerce.dto.response;

import com.example.ecommerce.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketResponse {

    private Long id;
    private Long orderId;
    private TicketStatus status;
    private RequesterDto requester;
    private Long approverId;
    private Integer currentLevel;
    private Integer requiredLevel;
    private LocalDateTime dueAt;
    private String lastComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequesterDto {
        private Long id;
        private String name;
        private String phone;
    }
}
