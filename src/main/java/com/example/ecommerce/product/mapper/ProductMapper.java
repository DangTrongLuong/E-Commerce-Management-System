package com.example.ecommerce.product.mapper;

import com.example.ecommerce.product.dto.ProductCreationRequest;
import com.example.ecommerce.product.dto.ProductUpdateRequest;
import com.example.ecommerce.product.dto.ProductResponse;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.user.entity.AppUser;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    Product toProduct(ProductCreationRequest creationRequest);

    @Mapping(source = "owner.id", target = "ownerId")
    @Mapping(target = "ownerName", expression = "java(getOwnerName(product.getOwner()))")
    ProductResponse toResponse(Product product);

    void updateProduct(
            ProductUpdateRequest productUpdateRequest,
            @MappingTarget Product product);

    default String getOwnerName(AppUser owner) {
        if (owner == null) {
            return "Hệ thống";
        }
        if (owner.getCustomer() != null && owner.getCustomer().getName() != null) {
            return owner.getCustomer().getName();
        }
        return owner.getEmail();
    }
}
