package com.example.ecommerce.mapper;

import com.example.ecommerce.dto.request.CustomerCreationRequest;
import com.example.ecommerce.dto.response.CustomerResponse;
import com.example.ecommerce.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    Customer createCustomer(CustomerCreationRequest request);

    CustomerResponse toResponse(Customer customer);

    void updateCustomer(
            CustomerCreationRequest request,
            @MappingTarget Customer customer
    );
}
