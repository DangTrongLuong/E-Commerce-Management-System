package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.OrderCreationRequest;
import com.example.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.example.ecommerce.dto.response.ApiResponse;
import com.example.ecommerce.dto.response.OrderResponse;
import com.example.ecommerce.dto.response.PageResponse;
import com.example.ecommerce.enums.OrderStatus;
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
@RequiredArgsConstructor
@RequestMapping("/api/orders")
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Tag(name = "Order", description = "Order catalog endpoints")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    OrderService orderService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Tạo đơn hàng mới (User / Admin)")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderCreationRequest request
    ) {
        OrderResponse orderResponse = orderService.createOrder(request);
        log.info("Tạo đơn hàng thành công: {}", orderResponse.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo đơn hàng thành công !", orderResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Lấy thông tin chi tiết 1 đơn hàng (User / Admin)")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Integer id) {
        OrderResponse orderResponse = orderService.getOrderById(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy thông tin đơn hàng thành công !", orderResponse));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy tất cả danh sách đơn hàng (Admin)")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách đơn hàng thành công !",
                        orderService.getAllOrders(status, page, size, sort)
                )
        );
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật trạng thái đơn hàng (Admin)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Integer id,
            @Valid @RequestBody OrderStatusUpdateRequest request
    ) {
        OrderResponse orderResponse = orderService.updateOrderStatus(id, request);
        log.info("Cập nhật trạng thái đơn hàng {} thành công", id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Cập nhật trạng thái đơn hàng thành công !", orderResponse));
    }
}
