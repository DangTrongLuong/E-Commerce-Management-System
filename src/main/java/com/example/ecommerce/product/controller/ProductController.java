package com.example.ecommerce.product.controller;

import com.example.ecommerce.product.entity.Product;

import com.example.ecommerce.product.dto.ProductCreationRequest;
import com.example.ecommerce.product.dto.ProductUpdateRequest;
import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.product.dto.ProductImportResultResponse;
import com.example.ecommerce.product.dto.ProductResponse;
import com.example.ecommerce.product.enums.ProductStatus;
import com.example.ecommerce.product.service.ProductBatchService;
import com.example.ecommerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/products")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Products", description = "Products catalog endpoints")
public class ProductController {

    ProductService productService;
    ProductBatchService productBatchService;

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Import products from CSV (PO / Admin)")
    public ResponseEntity<ApiResponse<ProductImportResultResponse>> importProducts(
            @RequestParam("file") MultipartFile file
    ) {
        ProductImportResultResponse result = productBatchService.importProductsFromCsv(file);
        return ResponseEntity.ok(ApiResponse.success("Nhập danh sách sản phẩm từ file CSV thành công", result));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Create a new product (PO / Admin)")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductCreationRequest productCreationRequest
    ) {
        ProductResponse productResponse = productService.createProduct(productCreationRequest);
        log.info("Product created successfully: {}", productResponse.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo sản phẩm thành công", productResponse));
    }

    @GetMapping
    @PreAuthorize("permitAll()")
    @Operation(summary = "Get list of products (Public / User / PO / Admin)")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProduct(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách sản phẩm thành công",
                        productService.getAllProduct(keyword, status, page, size, sort)
                )
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Get product details by ID (Public / User / PO / Admin)")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Integer id) {
        ProductResponse productResponse = productService.getProductById(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy thông tin chi tiết sản phẩm thành công", productResponse));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Update product details (PO / Admin)")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Integer id,
            @Valid @RequestBody ProductUpdateRequest productUpdateRequest
    ) {
        ProductResponse productResponse = productService.updateProduct(id, productUpdateRequest);
        log.info("Product updated successfully: {}", id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Cập nhật sản phẩm thành công", productResponse));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Delete product (PO / Admin)")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Xóa sản phẩm thành công", null));
    }
}
