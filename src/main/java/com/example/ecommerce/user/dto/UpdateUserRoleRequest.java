package com.example.ecommerce.user.dto;

import com.example.ecommerce.user.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserRoleRequest {

    @NotNull(message = "Vai trò tài khoản không được để trống")
    private Role role;
}
