package com.example.ecommerce.dto.request;

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

    @Size(min = 10, max = 500, message = "Comment must be between 10 and 500 characters")
    private String comment;
}
