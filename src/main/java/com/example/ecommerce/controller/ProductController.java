package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.ProductCreationRequest;
import com.example.ecommerce.dto.request.ProductUpdateRequest;
import com.example.ecommerce.dto.response.ApiResponse;
import com.example.ecommerce.dto.response.PageResponse;
import com.example.ecommerce.dto.response.ProductImportResultResponse;
import com.example.ecommerce.dto.response.ProductResponse;
import com.example.ecommerce.enums.ProductStatus;
import com.example.ecommerce.service.ProductBatchService;
import com.example.ecommerce.service.ProductService;
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
        return ResponseEntity.ok(ApiResponse.success("Import products successful", result));
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
                .body(ApiResponse.success("Product created successfully", productResponse));
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
                        "Get products successful",
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
                .body(ApiResponse.success("Get product successful", productResponse));
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
                .body(ApiResponse.success("Product updated successfully", productResponse));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Delete product (PO / Admin)")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Product deleted successfully", null));
    }
}
