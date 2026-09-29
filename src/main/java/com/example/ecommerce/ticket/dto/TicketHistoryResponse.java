package com.example.ecommerce.ticket.dto;

import com.example.ecommerce.ticket.enums.TicketAction;
import com.example.ecommerce.ticket.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketHistoryResponse {

    private Long id;
    private Long ticketId;
    private TicketAction action;
    private TicketStatus fromStatus;
    private TicketStatus toStatus;
    private Long actorId;
    private String comment;
    private LocalDateTime createdAt;
}
