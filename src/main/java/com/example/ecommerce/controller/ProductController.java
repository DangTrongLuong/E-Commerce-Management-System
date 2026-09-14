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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/products")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Products", description = "Products catalog endpoints")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    ProductService productService;
    ProductBatchService productBatchService;

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Import sản phẩm từ CSV (Admin)")
    public ResponseEntity<ApiResponse<ProductImportResultResponse>> importProducts(
            @RequestParam("file") MultipartFile file
    ) {
        ProductImportResultResponse result = productBatchService.importProductsFromCsv(file);
        return ResponseEntity.ok(ApiResponse.success("Import danh sách sản phẩm thành công !", result));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo 1 sản phẩm mới (Admin)")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductCreationRequest productCreationRequest
    ) {
        ProductResponse productResponse = productService.createProduct(productCreationRequest);
        log.info("Tạo sản phẩm thành công: {}", productResponse.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo sản phẩm thành công !", productResponse));
    }


    @GetMapping
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lấy danh sách các sản phẩm (Public / User / Admin)")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProduct(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách sản phẩm thành công !",
                        productService.getAllProduct(keyword, status, page, size, sort)
                )
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lấy thông tin chi tiết 1 sản phẩm (Public / User / Admin)")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Integer id) {
        ProductResponse productResponse = productService.getProductById(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy thành công sản phẩm!", productResponse));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Chỉnh sửa thông tin sản phẩm (Admin)")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Integer id,
            @Valid @RequestBody ProductUpdateRequest productUpdateRequest
    ) {
        ProductResponse productResponse = productService.updateProduct(id, productUpdateRequest);
        log.info("Cập nhật thành công sản phẩm: {}", id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Cập nhật sản phẩm thành công !", productResponse));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xóa sản phẩm (Admin)")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Xóa sản phẩm thành công !", null));
    }
}
