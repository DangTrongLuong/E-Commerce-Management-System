package com.example.ecommerce.customer.dto;

import com.example.ecommerce.customer.enums.CustomerStatus;
import com.example.ecommerce.common.validation.UniqueEmail;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerUpdateRequest {

    @NotBlank(message = "Tên không được để trống")
    @Size(max = 255, message = "Tên khách hàng quá dài !")
    private String name;

    @NotBlank(message = "Email không được trống !")
    @Email(message = "Định dạng email không hợp lệ")
    @UniqueEmail(message = "Email đã tồn tại !")
    private String email;

    @Pattern(
            regexp = "^[0-9]{10}$",
            message = "Số điện thoại phải có 10 chữ số"
    )
    private String phone;

    private CustomerStatus status;
}
