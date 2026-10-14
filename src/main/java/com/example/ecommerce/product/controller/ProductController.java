package com.example.ecommerce.product.controller;

import com.example.ecommerce.product.entity.Product;

import com.example.ecommerce.product.dto.ProductCreationRequest;
import com.example.ecommerce.product.dto.ProductUpdateRequest;
import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.product.dto.ProductImportResultResponse;
import com.example.ecommerce.product.dto.ProductResponse;
import com.example.ecommerce.product.enums.ProductStatus;
import com.example.ecommerce.product.service.CloudinaryService;
import com.example.ecommerce.product.service.ProductBatchService;
import com.example.ecommerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/products")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Sản Phẩm (Products)", description = "Quản lý danh mục sản phẩm, tải ảnh lên Cloudinary và import dữ liệu hàng loạt (Quyền: Công khai, PRODUCT_OWNER, ADMIN)")
public class ProductController {

    ProductService productService;
    ProductBatchService productBatchService;
    CloudinaryService cloudinaryService;

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Tải ảnh sản phẩm lên Cloudinary",
            description = "Nhận tệp tin hình ảnh sản phẩm (định dạng JPG/PNG/WEBP/GIF, dung lượng tối đa 5MB), tải trực tiếp lên kho lưu trữ đám mây Cloudinary và trả về đường dẫn URL ảnh công khai. Quyền truy cập: PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadProductImage(
            @Parameter(description = "File ảnh sản phẩm (tối đa 5MB, JPG/PNG/WEBP/GIF)")
            @RequestParam("file") MultipartFile file
    ) {
        String imageUrl = cloudinaryService.uploadImage(file);
        return ResponseEntity.ok(ApiResponse.success("Upload ảnh sản phẩm thành công", Map.of("imageUrl", imageUrl)));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Nhập danh sách sản phẩm hàng loạt từ file CSV",
            description = "Xử lý tệp dữ liệu CSV chứa danh sách sản phẩm. Tự động liên kết các sản phẩm hợp lệ với Product Owner đang thực hiện và tổng hợp báo cáo số lượng dòng thành công/thất bại kèm lý do lỗi. Quyền truy cập: PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<ProductImportResultResponse>> importProducts(
            @Parameter(description = "File CSV chứa danh sách sản phẩm")
            @RequestParam("file") MultipartFile file
    ) {
        ProductImportResultResponse result = productBatchService.importProductsFromCsv(file);
        return ResponseEntity.ok(ApiResponse.success("Nhập danh sách sản phẩm từ file CSV thành công", result));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Thêm mới một sản phẩm vào hệ thống",
            description = "Khởi tạo thông tin sản phẩm mới bao gồm tên, mô tả, giá niêm yết, số lượng tồn kho khả dụng và ảnh đại diện. Sản phẩm được gắn quyền sở hữu cho tài khoản tạo. Quyền truy cập: PRODUCT_OWNER, ADMIN."
    )
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
    @Operation(
            summary = "Lấy danh sách sản phẩm công khai",
            description = "Truy vấn danh mục sản phẩm công khai cho khách hàng và người dùng. Hỗ trợ tìm kiếm theo từ khóa (keyword), lọc theo trạng thái sản phẩm (status), phân trang và sắp xếp linh hoạt. Quyền truy cập: Công khai (Không cần đăng nhập)."
    )
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

    @GetMapping("/mine")
    @PreAuthorize("hasRole('PRODUCT_OWNER')")
    @Operation(
            summary = "Lấy danh sách sản phẩm do chính PO quản lý",
            description = "Truy vấn danh sách các sản phẩm thuộc quyền sở hữu quản lý của tài khoản Product Owner đang đăng nhập. Hỗ trợ tìm kiếm theo từ khóa, lọc trạng thái và phân trang. Quyền truy cập: PRODUCT_OWNER."
    )
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getMyProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách sản phẩm của bạn thành công",
                productService.getMyProducts(keyword, status, page, size, sort)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("permitAll()")
    @Operation(
            summary = "Lấy thông tin chi tiết sản phẩm theo ID",
            description = "Tra cứu toàn bộ thông tin chi tiết của sản phẩm bao gồm tên gọi, đơn giá, số lượng tồn kho còn lại, mô tả chi tiết và hình ảnh minh họa. Quyền truy cập: Công khai."
    )
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Integer id) {
        ProductResponse productResponse = productService.getProductById(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy thông tin chi tiết sản phẩm thành công", productResponse));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Cập nhật thông tin chi tiết sản phẩm",
            description = "Chỉnh sửa các trường thông tin sản phẩm (tên, giá bán, số lượng trong kho, mô tả, ảnh, trạng thái kinh doanh). Product Owner chỉ được sửa sản phẩm của chính mình, ADMIN có toàn quyền sửa mọi sản phẩm. Quyền truy cập: PRODUCT_OWNER, ADMIN."
    )
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
    @Operation(
            summary = "Xóa sản phẩm khỏi hệ thống",
            description = "Xóa sản phẩm theo mã định danh ID. PO chỉ được xóa sản phẩm của mình, ADMIN có toàn quyền xóa. Quyền truy cập: PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Xóa sản phẩm thành công", null));
    }
}
