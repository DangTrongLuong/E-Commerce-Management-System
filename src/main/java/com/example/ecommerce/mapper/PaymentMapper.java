package com.example.ecommerce.mapper;

import com.example.ecommerce.dto.response.PageResponse;
import com.example.ecommerce.dto.response.PaymentResponse;
import com.example.ecommerce.entity.PaymentTransaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponse toResponse(PaymentTransaction entity);
}
