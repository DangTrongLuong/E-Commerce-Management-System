package com.example.ecommerce.order.controller;

import com.example.ecommerce.order.dto.OrderCreationRequest;
import com.example.ecommerce.order.dto.OrderStatusUpdateRequest;
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
@RequestMapping("/api")
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Tag(name = "Order", description = "Order processing endpoints")
public class OrderController {

    OrderService orderService;
    PdfInvoiceService pdfInvoiceService;

    @PostMapping("/orders")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create a new order (Supports Multi-PO Auto Split) (USER)")
    public ResponseEntity<ApiResponse<java.util.List<OrderResponse>>> createOrder(
            @Valid @RequestBody OrderCreationRequest request) {
        List<OrderResponse> orderResponses = orderService.createOrders(request);
        log.info("Successfully created {} order(s)", orderResponses.size());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order(s) created successfully", orderResponses));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get order details by ID (User / PO / Admin)")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Integer id) {
        OrderResponse orderResponse = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success("Fetched order details successfully", orderResponse));
    }

    @GetMapping(value = "/orders/{id}/pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Export / Download PDF Invoice for approved order (CONFIRMED / PROCESSING / COMPLETED)")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Integer id) {
        orderService.getOrderById(id);
        byte[] pdfBytes = pdfInvoiceService.generateInvoicePdf(id);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"invoice_order_" + id + ".pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/orders")
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

    @GetMapping("/customers/{customerId}/orders")
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

    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Update order status (PO / Admin - CONFIRMED to COMPLETED only)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Integer id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        OrderResponse orderResponse = orderService.updateOrderStatus(id, request);
        log.info("Order {} status updated successfully", id);
        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", orderResponse));
    }

    @PostMapping("/orders/{id}/refund-request")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Request order refund (USER / ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> requestRefund(
            @PathVariable Integer id,
            @Valid @RequestBody OrderRefundRequest request) {
        OrderResponse response = orderService.requestRefund(id, request);
        return ResponseEntity.ok(ApiResponse.success("Yêu cầu hoàn tiền đã được gửi thành công", response));
    }

    @PostMapping("/orders/{id}/refund-approve")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Approve order refund (PO / ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> approveRefund(
            @PathVariable Integer id,
            @RequestBody(required = false) OrderRefundActionRequest request) {
        OrderResponse response = orderService.approveRefund(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đã duyệt hoàn tiền thành công", response));
    }

    @PostMapping("/orders/{id}/refund-reject")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Reject order refund (PO / ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> rejectRefund(
            @PathVariable Integer id,
            @RequestBody(required = false) OrderRefundActionRequest request) {
        OrderResponse response = orderService.rejectRefund(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối yêu cầu hoàn tiền", response));
    }
}
