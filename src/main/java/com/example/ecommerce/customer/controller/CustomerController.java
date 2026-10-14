package com.example.ecommerce.customer.controller;

import com.example.ecommerce.customer.entity.Customer;

import com.example.ecommerce.customer.dto.CustomerCreationRequest;
import com.example.ecommerce.customer.dto.CustomerUpdateRequest;
import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.customer.dto.CustomerResponse;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.customer.service.CustomerService;
import com.example.ecommerce.order.service.OrderService;
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
@Tag(name = "Khách Hàng (Customers)", description = "Quản lý thông tin hồ sơ khách hàng và địa chỉ liên lạc (Quyền: USER, ADMIN)")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);
    CustomerService customerService;
    OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Tạo mới hồ sơ khách hàng",
            description = "Tạo thông tin hồ sơ khách hàng mới (họ tên, email, số điện thoại, địa chỉ nhận hàng). Quyền truy cập: ADMIN."
    )
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
    @Operation(
            summary = "Lấy danh sách khách hàng có phân trang",
            description = "Truy vấn danh sách thông tin khách hàng trong hệ thống theo định dạng phân trang (page, size). Quyền truy cập: ADMIN."
    )
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
    @Operation(
            summary = "Lấy thông tin chi tiết một khách hàng theo ID",
            description = "Tra cứu chi tiết thông tin cá nhân và địa chỉ nhận hàng của khách hàng theo mã ID. Quyền truy cập: USER (chính chủ) hoặc ADMIN."
    )
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerById(@PathVariable Integer id) {
        CustomerResponse customerResponse = customerService.getCustomerById(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy thành công khách hàng!", customerResponse));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Cập nhật thông tin chi tiết khách hàng",
            description = "Cập nhật các trường thông tin của khách hàng như họ tên, số điện thoại và địa chỉ giao hàng. Quyền truy cập: USER (chính chủ) hoặc ADMIN."
    )
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
    @Operation(
            summary = "Xóa hồ sơ khách hàng khỏi hệ thống",
            description = "Xóa thông tin khách hàng theo mã ID nếu khách hàng không còn đơn hàng nào đang liên kết hoặc xử lý. Quyền truy cập: ADMIN."
    )
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable Integer id) {
        customerService.deteleCustomer(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Xóa khách hàng thành công !", null));
    }

//    @GetMapping("/{id}/orders")
//    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
//    @Operation(summary = "Lấy danh sách đơn hàng của 1 khách hàng (User / Admin)")
//    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrdersByCustomer(
//            @PathVariable("id") Integer customerId,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size,
//            @RequestParam(required = false) OrderStatus status,
//            @RequestParam(required = false) String sort
//    ) {
//        return ResponseEntity.ok(
//                ApiResponse.success(
//                        "Lấy danh sách đơn hàng của khách hàng thành công !",
//                        orderService.getOrdersByCustomer(customerId, status, page, size, sort)
//                )
//        );
//    }
}
