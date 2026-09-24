package com.example.ecommerce.dto.response;

import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.UserStatus;
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

    private Long id;
    private String email;
    private Role role;
    private UserStatus status;
    private Long customerId;
    private LocalDateTime createdAt;
}
