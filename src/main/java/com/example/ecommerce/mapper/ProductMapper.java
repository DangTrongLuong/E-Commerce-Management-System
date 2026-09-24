package com.example.ecommerce.mapper;

import com.example.ecommerce.dto.request.ProductCreationRequest;
import com.example.ecommerce.dto.request.ProductUpdateRequest;
import com.example.ecommerce.dto.response.ProductResponse;
import com.example.ecommerce.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    Product toProduct(ProductCreationRequest creationRequest);

    @Mapping(source = "owner.id", target = "ownerId")
    @Mapping(source = "owner.customer.name", target = "ownerName")
    ProductResponse toResponse(Product product);

    void updateProduct(
            ProductUpdateRequest productUpdateRequest,
            @MappingTarget Product product
    );
}
