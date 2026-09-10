package com.example.ecommerce.dto.response;

import com.example.ecommerce.enums.CustomerStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    private int id;

    private String name;

    private String email;

    private String phone;

    private CustomerStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
