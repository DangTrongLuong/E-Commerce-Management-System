package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.ProductCreationRequest;
import com.example.ecommerce.dto.request.ProductUpdateRequest;
import com.example.ecommerce.dto.response.ApiResponse;
import com.example.ecommerce.dto.response.PageResponse;
import com.example.ecommerce.dto.response.ProductResponse;
import com.example.ecommerce.enums.ProductStatus;
import com.example.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/products")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Products", description = "Products catalog endpoints")
public class ProductController {
    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    ProductService productService;

    @PostMapping
    @Operation(summary = "Tạo 1 sản phẩm mới")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductCreationRequest productCreationRequest
            ){
        ProductResponse productResponse = productService.createProduct(productCreationRequest);
        log.info("Tạo sản phẩm thành công: {}", productResponse.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo sản phẩm thành công !", productResponse));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách các sản phẩm")
    public  ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProduct(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String sort
    ){
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách sản phẩm thành công !",
                        productService.getAllProduct(
                                keyword,
                                status,
                                page,
                                size,
                                sort
                        )
                )
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chi tiết 1 sản phẩm !")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Integer id){
        ProductResponse productResponse = productService.getProductById(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(
                                "Lấy thành công sản pẩm!",
                                productResponse
                        )
                );

    }

    @PutMapping("/{id}")
    @Operation(summary = "Chỉnh sửa thông tin sản phẩm")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Integer id,
            @Valid @RequestBody ProductUpdateRequest productUpdateRequest
            ){
        ProductResponse productResponse = productService.updateProduct(id, productUpdateRequest);
        log.info("Cập nhật thành công sản phẩm: {}", id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Cập nhật sản phẩm thành công !", productResponse));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa sản phẩm")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Integer id){
        productService.deleteProduct(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(
                        "Xóa sản phẩm thành công !",
                        null
                ));
    }


}
