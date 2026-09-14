package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.CustomerCreationRequest;
import com.example.ecommerce.dto.request.CustomerUpdateRequest;
import com.example.ecommerce.dto.response.ApiResponse;
import com.example.ecommerce.dto.response.CustomerResponse;
import com.example.ecommerce.dto.response.OrderResponse;
import com.example.ecommerce.dto.response.PageResponse;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.service.CustomerService;
import com.example.ecommerce.service.OrderService;
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

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customer", description = "Customer catalog endpoints")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);
    CustomerService customerService;
    OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo 1 khách hàng mới (Admin)")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CustomerCreationRequest customerCreationRequest
    ) {
        CustomerResponse customerResponse = customerService.createCustomer(customerCreationRequest);
        log.info("Tạo khách hàng thành công: {}", customerResponse.getId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo khách hàng thành công !", customerResponse));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy danh sách khách hàng bằng phân trang (Admin)")
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> getAllCustomer(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(
                        "Lấy danh sách phân trang thành công",
                        customerService.getAllCustomer(page, size))
                );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Lấy thông tin chi tiết 1 khách hàng (User / Admin)")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerById(@PathVariable Integer id) {
        CustomerResponse customerResponse = customerService.getCustomerById(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy thành công khách hàng!", customerResponse));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Chỉnh sửa thông tin khách hàng (User / Admin)")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable Integer id,
            @Valid @RequestBody CustomerUpdateRequest customerUpdateRequest
    ) {
        CustomerResponse customerResponse = customerService.updateCustomer(id, customerUpdateRequest);
        log.info("Cập nhật thành công khách hàng: {}", id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Cập nhật khách hàng thành công !", customerResponse));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xóa khách hàng (Admin)")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable Integer id) {
        customerService.deteleCustomer(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Xóa khách hàng thành công !", null));
    }

    @GetMapping("/{id}/orders")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Lấy danh sách đơn hàng của 1 khách hàng (User / Admin)")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrdersByCustomer(
            @PathVariable("id") Integer customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách đơn hàng của khách hàng thành công !",
                        orderService.getOrdersByCustomer(customerId, status, page, size, sort)
                )
        );
    }
}
