package com.example.ecommerce.dto.response;

import com.example.ecommerce.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {

    private int id;
    private String email;
    private Role role;
    private Integer customerId;
    private LocalDateTime createdAt;
}
