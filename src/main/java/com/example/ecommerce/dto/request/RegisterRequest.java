package com.example.ecommerce.dto.request;

import com.example.ecommerce.validation.UniqueEmail;
import com.example.ecommerce.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Tên không được để trống")
    private String name;

    @NotBlank(message = "Email không được để trống")
    @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@gmail\\.com$",
            message = "Email phải đúng định dạng và có đuôi @gmail.com"
    )
    @UniqueEmail
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @ValidPassword
    private String password;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(
            regexp = "^0\\d{9}$",
            message = "Số điện thoại phải bắt đầu bằng số 0 và có đủ 10 chữ số"
    )
    private String phone;
}