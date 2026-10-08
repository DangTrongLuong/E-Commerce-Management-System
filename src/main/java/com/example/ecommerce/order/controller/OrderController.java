package com.example.ecommerce.order.controller;

import com.example.ecommerce.order.dto.OrderCreationRequest;
import com.example.ecommerce.order.dto.OrderItemRequest;
import com.example.ecommerce.order.dto.OrderStatusUpdateRequest;
import com.example.ecommerce.order.dto.UpdateOrderItemQuantityRequest;
import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ecommerce.order.service.PdfInvoiceService;

import com.example.ecommerce.order.dto.OrderRefundActionRequest;
import com.example.ecommerce.order.dto.OrderRefundRequest;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Tag(name = "Order", description = "Order processing endpoints")
public class OrderController {

    OrderService orderService;
    PdfInvoiceService pdfInvoiceService;

    // =========================================================================
    // 1. TẠO ĐƠN HÀNG (ORDER CREATION)
    // =========================================================================

    // Tạo mới đơn hàng (hỗ trợ tự động tách đơn theo PO)
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create a new order (Supports Multi-PO Auto Split) (USER)")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> createOrder(
            @Valid @RequestBody OrderCreationRequest request) {
        List<OrderResponse> orderResponses = orderService.createOrders(request);
        log.info("Successfully created {} order(s)", orderResponses.size());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order(s) created successfully", orderResponses));
    }

    // =========================================================================
    // 2. CHỈNH SỬA SẢN PHẨM & XÓA ĐƠN HÀNG (ITEM & ORDER EDITING - REQ-01)
    // =========================================================================

    // Thêm sản phẩm vào đơn hàng PENDING và gộp số lượng
    @PostMapping("/{orderId}/items")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Thêm sản phẩm vào đơn hàng PENDING và gộp số lượng (USER)")
    public ResponseEntity<ApiResponse<OrderResponse>> addItemToOrder(
            @PathVariable Integer orderId,
            @Valid @RequestBody OrderItemRequest request) {
        OrderResponse orderResponse = orderService.addItemToOrder(orderId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm sản phẩm vào đơn hàng thành công", orderResponse));
    }

    // Cập nhật số lượng sản phẩm trong đơn hàng PENDING
    @PatchMapping("/{orderId}/items/{productId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Cập nhật số lượng sản phẩm trong đơn hàng PENDING (USER)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderItemQuantity(
            @PathVariable Integer orderId,
            @PathVariable Integer productId,
            @Valid @RequestBody UpdateOrderItemQuantityRequest request) {
        OrderResponse response = orderService.updateOrderItemQuantity(orderId, productId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật số lượng sản phẩm thành công", response));
    }

    // Xóa một sản phẩm khỏi đơn hàng PENDING và hoàn lại tồn kho
    @DeleteMapping("/{orderId}/items/{productId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Xóa sản phẩm khỏi đơn hàng PENDING và hoàn lại tồn kho (USER)")
    public ResponseEntity<ApiResponse<OrderResponse>> deleteOrderItem(
            @PathVariable Integer orderId,
            @PathVariable Integer productId) {
        OrderResponse response = orderService.deleteOrderItem(orderId, productId);
        return ResponseEntity.ok(ApiResponse.success("Xóa sản phẩm khỏi đơn hàng thành công", response));
    }

    // Xóa toàn bộ đơn hàng PENDING và hoàn lại tồn kho cho tất cả sản phẩm
    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Xóa toàn bộ đơn hàng PENDING và hoàn lại tồn kho cho tất cả sản phẩm (USER)")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable Integer orderId) {
        orderService.deleteOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa đơn hàng và hoàn tồn kho thành công", null));
    }

    // =========================================================================
    // 3. TRUY VẤN ĐƠN HÀNG (ORDER QUERY & GETTERS)
    // =========================================================================

    // Lấy chi tiết đơn hàng theo ID
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get order details by ID (User / PO / Admin)")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Integer id) {
        OrderResponse orderResponse = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success("Fetched order details successfully", orderResponse));
    }

    // Xuất / Tải xuống hóa đơn PDF cho đơn hàng đã phê duyệt
    @GetMapping(value = "/{id}/pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Export / Download PDF Invoice for approved order (CONFIRMED / PROCESSING / COMPLETED)")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Integer id) {
        orderService.getOrderById(id);
        byte[] pdfBytes = pdfInvoiceService.generateInvoicePdf(id);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"invoice_order_" + id + ".pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // Lấy danh sách đơn hàng (User / PO / Admin)
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get list of orders (User / PO / Admin)")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.success("Fetched orders list successfully",
                orderService.getAllOrders(status, page, size, sort)));
    }

    // Lấy danh sách đơn hàng theo ID khách hàng
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get customer's orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrdersByCustomer(
            @PathVariable Integer customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.success("Fetched customer orders successfully",
                orderService.getOrdersByCustomer(customerId, status, page, size, sort)));
    }

    // =========================================================================
    // 4. CẬP NHẬT TRẠNG THÁI & HOÀN TIỀN (STATUS UPDATE & REFUND WORKFLOW)
    // =========================================================================

    // Cập nhật trạng thái đơn hàng (CONFIRMED -> COMPLETED cho PO / Admin)
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Update order status (PO / Admin - CONFIRMED to COMPLETED only)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Integer id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        OrderResponse orderResponse = orderService.updateOrderStatus(id, request);
        log.info("Order {} status updated successfully", id);
        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", orderResponse));
    }

    // Yêu cầu hoàn tiền đơn hàng
    @PostMapping("/{id}/refund-request")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Request order refund (USER / ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> requestRefund(
            @PathVariable Integer id,
            @Valid @RequestBody OrderRefundRequest request) {
        OrderResponse response = orderService.requestRefund(id, request);
        return ResponseEntity.ok(ApiResponse.success("Yêu cầu hoàn tiền đã được gửi thành công", response));
    }

    // Duyệt yêu cầu hoàn tiền (PO / Admin)
    @PostMapping("/{id}/refund-approve")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Approve order refund (PO / ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> approveRefund(
            @PathVariable Integer id,
            @RequestBody(required = false) OrderRefundActionRequest request) {
        OrderResponse response = orderService.approveRefund(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đã duyệt hoàn tiền thành công", response));
    }

    // Từ chối yêu cầu hoàn tiền (PO / Admin)
    @PostMapping("/{id}/refund-reject")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Reject order refund (PO / ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> rejectRefund(
            @PathVariable Integer id,
            @RequestBody(required = false) OrderRefundActionRequest request) {
        OrderResponse response = orderService.rejectRefund(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối yêu cầu hoàn tiền", response));
    }
}
