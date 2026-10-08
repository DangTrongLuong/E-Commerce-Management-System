package com.example.ecommerce.ticket.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketActionRequest {

    @Size(min = 10, max = 500, message = "Ghi chú phải có độ dài từ 10 đến 500 ký tự")
    private String comment;
}
