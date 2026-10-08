package com.example.ecommerce.user.dto;

import com.example.ecommerce.user.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserStatusRequest {

    @NotNull(message = "Trạng thái tài khoản không được để trống")
    private UserStatus status;
}
