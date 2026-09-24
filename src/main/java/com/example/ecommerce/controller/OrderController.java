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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ecommerce.service.PdfInvoiceService;

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
    @Operation(summary = "Create a new order (USER)")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderCreationRequest request
    ) {
        OrderResponse orderResponse = orderService.createOrder(request);
        log.info("Order created successfully: {}", orderResponse.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created successfully", orderResponse));
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
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"invoice_order_" + id + ".pdf\"")
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
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(ApiResponse.success("Fetched orders list successfully", orderService.getAllOrders(status, page, size, sort)));
    }

    @GetMapping("/customers/{customerId}/orders")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get customer's orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrdersByCustomer(
            @PathVariable Integer customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(ApiResponse.success("Fetched customer orders successfully", orderService.getOrdersByCustomer(customerId, status, page, size, sort)));
    }

    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Update order status (PO / Admin - CONFIRMED to COMPLETED only)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Integer id,
            @Valid @RequestBody OrderStatusUpdateRequest request
    ) {
        OrderResponse orderResponse = orderService.updateOrderStatus(id, request);
        log.info("Order {} status updated successfully", id);
        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", orderResponse));
    }
}
