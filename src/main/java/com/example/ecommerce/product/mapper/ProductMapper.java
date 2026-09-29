package com.example.ecommerce.product.mapper;

import com.example.ecommerce.product.dto.ProductCreationRequest;
import com.example.ecommerce.product.dto.ProductUpdateRequest;
import com.example.ecommerce.product.dto.ProductResponse;
import com.example.ecommerce.product.entity.Product;
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
