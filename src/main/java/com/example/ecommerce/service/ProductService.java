package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.ProductCreationRequest;
import com.example.ecommerce.dto.request.ProductUpdateRequest;
import com.example.ecommerce.dto.response.PageResponse;
import com.example.ecommerce.dto.response.ProductResponse;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.enums.ProductStatus;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.mapper.ProductMapper;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.util.SortUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ProductService {
    ProductRepository productRepository;
    ProductMapper productMapper;

    public ProductResponse createProduct(ProductCreationRequest productCreationRequest){
        boolean nameExists = productRepository
                .findByNameIgnoreCase(productCreationRequest.getName())
                .isPresent();

        if (nameExists) {
            throw new ConflictException(
                    "Sản phẩm với tên '" + productCreationRequest.getName()
                            + "' đã tồn tại.");
        }

        Product product = productMapper.toProduct(productCreationRequest);

        if(product.getStock() == null) product.setStock(0);
        return  productMapper.toResponse(productRepository.save(product));
    }

//    public PageResponse<ProductResponse> getAllProduct(int page, int size){
//        Pageable pageable = PageRequest.of(page, size);
//        Page<Product> productPage = productRepository.findAll(pageable);
//        Page<ProductResponse> productResponsePage = productPage.map(productMapper::toResponse);
//        return PageResponse.of(productResponsePage);
//    }

    public PageResponse<ProductResponse> getAllProduct(
            String keyword,
            ProductStatus status,
            int page,
            int size,
            String sort){

        Sort sorting = SortUtils.buildSort(sort, "id");
        Pageable pageable = PageRequest.of(page, size, sorting);

        Specification<Product> specification = Specification.unrestricted();

        if (keyword != null && !keyword.isBlank()) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.like(
                                    cb.lower(root.get("name")),
                                    "%" + keyword.toLowerCase() + "%"
                            )
            );
        }

        if (status != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(root.get("status"), status)
            );
        }

        Page<Product> productPage = productRepository.findAll(specification,pageable);
        Page<ProductResponse> productResponsePage = productPage.map(productMapper::toResponse);

        return PageResponse.of(productResponsePage);
    }

    public ProductResponse getProductById(int id){
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin sản phẩm"));

        return productMapper.toResponse(product);
    }

    public ProductResponse updateProduct(int id, ProductUpdateRequest productUpdateRequest){
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));

        productMapper.updateProduct(productUpdateRequest, product);
        return productMapper.toResponse(
                productRepository.save(product)
        );

    }

    public void deleteProduct(int id){
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm tương ứng !"));

        if(!product.getOrderItems().isEmpty()){
            throw new ConflictException("Không thể xóa sản phẩm đã có trong đơn hàng !");
        }

        productRepository.save(product);
    }


}
