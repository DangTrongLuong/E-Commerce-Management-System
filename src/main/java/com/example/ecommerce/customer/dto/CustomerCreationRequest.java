package com.example.ecommerce.customer.dto;

import com.example.ecommerce.customer.enums.CustomerStatus;
import com.example.ecommerce.common.validation.UniqueEmail;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerCreationRequest {

    @NotBlank(message = "Tên khách hàng không được trống !")
    @Size(max = 255, message = "Tên khách hàng quá dài !")
    private String name;

    @NotBlank(message = "Email không được trống !")
    @Email(message = "Định dạng email không hợp lệ")
    @UniqueEmail(message = "Email đã tồn tại !")
    private String email;

    @Size(max = 10, message = "Số điện thoại phải đủ 10 chữ số !")
    private String phone;

}
