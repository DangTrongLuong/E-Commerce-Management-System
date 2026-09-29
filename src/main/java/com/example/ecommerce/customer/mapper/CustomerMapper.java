package com.example.ecommerce.customer.mapper;

import com.example.ecommerce.customer.dto.CustomerCreationRequest;
import com.example.ecommerce.customer.dto.CustomerUpdateRequest;
import com.example.ecommerce.customer.dto.CustomerResponse;
import com.example.ecommerce.customer.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    Customer createCustomer(CustomerCreationRequest request);

    CustomerResponse toResponse(Customer customer);

    void updateCustomer(
            CustomerUpdateRequest request,
            @MappingTarget Customer customer
    );
}
