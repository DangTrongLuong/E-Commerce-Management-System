package com.example.ecommerce.payment.mapper;

import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.payment.dto.PaymentResponse;
import com.example.ecommerce.payment.entity.PaymentTransaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponse toResponse(PaymentTransaction entity);
}
