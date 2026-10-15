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
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ecommerce.order.dto.OrderCheckoutResponse;
import com.example.ecommerce.order.service.PdfInvoiceService;

import com.example.ecommerce.order.dto.OrderRefundActionRequest;
import com.example.ecommerce.order.dto.OrderRefundRequest;
import com.example.ecommerce.order.dto.OrderStatusHistoryResponse;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Tag(name = "Đơn Hàng (Orders)", description = "Quản lý quy trình đặt hàng, checkout, cập nhật trạng thái và hoàn tiền (Quyền: USER, PRODUCT_OWNER, ADMIN)")
public class OrderController {

    OrderService orderService;
    PdfInvoiceService pdfInvoiceService;

    // =========================================================================
    // 1. TẠO ĐƠN HÀNG (ORDER CREATION)
    // =========================================================================

    // Tạo mới đơn hàng (hỗ trợ tự động tách đơn theo PO)
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Tạo mới đơn hàng (Tự động tách đơn theo PO)",
            description = "Tạo đơn hàng mới từ danh sách mặt hàng đã chọn. Hệ thống tự động phân loại và tách thành các đơn hàng độc lập theo từng Product Owner (PO), kiểm tra và giữ trước số lượng tồn kho khả dụng. Quyền truy cập: USER, ADMIN."
    )
    public ResponseEntity<ApiResponse<List<OrderResponse>>> createOrder(
            @Valid @RequestBody OrderCreationRequest request) {
        List<OrderResponse> orderResponses = orderService.createOrders(request);
        log.info("Successfully created {} order(s)", orderResponses.size());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo đơn hàng thành công", orderResponses));
    }

    // Checkout đơn hàng (REQ-02 - Idempotent Checkout)
    @PostMapping("/{id}/checkout")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Thanh toán đơn hàng (Hỗ trợ Idempotent & VNPay/COD)",
            description = "Thực hiện thanh toán đơn hàng đang ở trạng thái PENDING. Hỗ trợ header 'Idempotency-Key' để chống trừ tiền/tạo giao dịch lặp lại. Nếu chọn VNPay sẽ tạo URL chuyển hướng tới cổng thanh toán VNPay. Quyền truy cập: USER (chủ đơn) hoặc ADMIN."
    )
    public ResponseEntity<ApiResponse<OrderCheckoutResponse>> checkoutOrder(
            @PathVariable Integer id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(required = false) String bankCode,
            HttpServletRequest request) {
        OrderCheckoutResponse response = orderService.checkoutOrder(id, idempotencyKey, bankCode, request);
        return ResponseEntity.ok(ApiResponse.success("Checkout đơn hàng thành công", response));
    }

    // =========================================================================
    // 2. CHỈNH SỬA SẢN PHẨM & XÓA ĐƠN HÀNG (ITEM & ORDER EDITING - REQ-01)
    // =========================================================================

    // Thêm sản phẩm vào đơn hàng PENDING và gộp số lượng
    @PostMapping("/{orderId}/items")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Thêm sản phẩm vào đơn hàng PENDING",
            description = "Thêm một sản phẩm vào đơn hàng đang chờ thanh toán (PENDING). Nếu sản phẩm đã có trong đơn thì tự động gộp số lượng và giữ thêm tồn kho tương ứng. Quyền truy cập: USER, ADMIN."
    )
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
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Cập nhật số lượng mặt hàng trong đơn PENDING",
            description = "Điều chỉnh số lượng của một sản phẩm trong đơn PENDING. Tự động kiểm tra tồn kho tăng/giảm, hoàn lại phần dư thừa vào kho và tính toán lại tổng tiền đơn hàng. Quyền truy cập: USER, ADMIN."
    )
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderItemQuantity(
            @PathVariable Integer orderId,
            @PathVariable Integer productId,
            @Valid @RequestBody UpdateOrderItemQuantityRequest request) {
        OrderResponse response = orderService.updateOrderItemQuantity(orderId, productId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật số lượng sản phẩm thành công", response));
    }

    // Xóa một sản phẩm khỏi đơn hàng PENDING và hoàn lại tồn kho
    @DeleteMapping("/{orderId}/items/{productId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Xóa sản phẩm khỏi đơn hàng PENDING",
            description = "Gỡ bỏ một sản phẩm khỏi đơn PENDING, hoàn trả lại toàn bộ số lượng tồn kho đã giữ của mặt hàng đó và cập nhật lại tổng tiền. Quyền truy cập: USER, ADMIN."
    )
    public ResponseEntity<ApiResponse<OrderResponse>> deleteOrderItem(
            @PathVariable Integer orderId,
            @PathVariable Integer productId) {
        OrderResponse response = orderService.deleteOrderItem(orderId, productId);
        return ResponseEntity.ok(ApiResponse.success("Xóa sản phẩm khỏi đơn hàng thành công", response));
    }

    // Xóa toàn bộ đơn hàng PENDING và hoàn lại tồn kho cho tất cả sản phẩm
    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Hủy và xóa toàn bộ đơn hàng PENDING",
            description = "Hủy bỏ đơn hàng PENDING, giải phóng và hoàn trả lại kho toàn bộ số lượng tồn kho của tất cả sản phẩm trong đơn. Quyền truy cập: USER, ADMIN."
    )
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable Integer orderId) {
        orderService.deleteOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa đơn hàng và hoàn tồn kho thành công", null));
    }

    // =========================================================================
    // 3. TRUY VẤN ĐƠN HÀNG (ORDER QUERY & GETTERS)
    // =========================================================================

    // Đếm số lượng đơn hàng theo từng trạng thái (cho tab filter đơn hàng của khách)
    @GetMapping("/status-counts")
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Đếm số lượng đơn hàng theo từng trạng thái của khách hàng",
            description = "Trả về số lượng đơn hàng theo từng trạng thái (kèm tổng số đơn ALL) phục vụ hiển thị số đếm (Badge) trên các tab lọc đơn hàng. Quyền truy cập: USER (tự động đếm theo tài khoản của khách hàng đang đăng nhập)."
    )
    public ResponseEntity<ApiResponse<Map<String, Long>>> getOrderStatusCounts() {
        Map<String, Long> counts = orderService.getOrderStatusCounts();
        return ResponseEntity.ok(ApiResponse.success("Lấy số lượng đơn hàng theo trạng thái thành công", counts));
    }

    // Lấy chi tiết đơn hàng theo ID
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Lấy thông tin chi tiết đơn hàng theo ID",
            description = "Xem chi tiết một đơn hàng gồm danh sách mặt hàng, đơn giá, địa chỉ giao hàng, thông tin thanh toán và trạng thái hiện tại. Quyền truy cập: USER (chủ đơn), PRODUCT_OWNER (đơn thuộc quyền), ADMIN."
    )
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Integer id) {
        OrderResponse orderResponse = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin chi tiết đơn hàng thành công", orderResponse));
    }

    // Xuất / Tải xuống hóa đơn PDF cho đơn hàng đã phê duyệt
    @GetMapping(value = "/{id}/pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Tải hóa đơn điện tử PDF của đơn hàng",
            description = "Tạo và tải về hóa đơn bán hàng định dạng PDF cho đơn hàng đã được xác nhận hoặc hoàn tất (CONFIRMED, PROCESSING, COMPLETED). Quyền truy cập: USER (chủ đơn), PRODUCT_OWNER, ADMIN."
    )
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
    @Operation(
            summary = "Lấy danh sách đơn hàng có phân trang và bộ lọc",
            description = "Truy vấn danh sách đơn hàng theo trạng thái (status), hỗ trợ phân trang và sắp xếp. Dữ liệu tự động lọc theo quyền: USER chỉ thấy đơn của mình, PO chỉ thấy đơn thuộc sản phẩm của mình, ADMIN thấy toàn bộ. Quyền truy cập: Đã đăng nhập."
    )
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng thành công",
                orderService.getAllOrders(status, page, size, sort)));
    }

    // Lấy danh sách đơn hàng theo ID khách hàng
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Lấy danh sách đơn hàng theo mã khách hàng",
            description = "Truy vấn danh sách đơn hàng của một khách hàng cụ thể theo customerId kèm phân trang và lọc theo trạng thái. Quyền truy cập: USER (chính chủ) hoặc ADMIN."
    )
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrdersByCustomer(
            @PathVariable Integer customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng của khách hàng thành công",
                orderService.getOrdersByCustomer(customerId, status, page, size, sort)));
    }

    // =========================================================================
    // 4. CẬP NHẬT TRẠNG THÁI & HOÀN TIỀN (STATUS UPDATE & REFUND WORKFLOW)
    // =========================================================================

    // Cập nhật trạng thái đơn hàng (CONFIRMED -> COMPLETED cho PO / Admin)
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Cập nhật trạng thái xử lý đơn hàng",
            description = "Chuyển tiến trình xử lý đơn hàng (ví dụ: chuyển sang PROCESSING, SHIPPING hoặc COMPLETED) và tự động ghi log lịch sử trạng thái. Quyền truy cập: PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Integer id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        OrderResponse orderResponse = orderService.updateOrderStatus(id, request);
        log.info("Order {} status updated successfully", id);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái đơn hàng thành công", orderResponse));
    }

    // Yêu cầu hoàn tiền đơn hàng
    @PostMapping("/{id}/refund-request")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Gửi yêu cầu hoàn tiền cho đơn hàng",
            description = "Khách hàng gửi yêu cầu hoàn tiền cho đơn hàng kèm theo lý do cụ thể. Đơn hàng chuyển sang trạng thái chờ duyệt hoàn tiền (REFUND_PENDING). Quyền truy cập: USER, ADMIN."
    )
    public ResponseEntity<ApiResponse<OrderResponse>> requestRefund(
            @PathVariable Integer id,
            @Valid @RequestBody OrderRefundRequest request) {
        OrderResponse response = orderService.requestRefund(id, request);
        return ResponseEntity.ok(ApiResponse.success("Yêu cầu hoàn tiền đã được gửi thành công", response));
    }

    // Duyệt yêu cầu hoàn tiền (PO / Admin)
    @PostMapping("/{id}/refund-approve")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Phê duyệt yêu cầu hoàn tiền đơn hàng",
            description = "Chấp thuận yêu cầu hoàn tiền, hoàn trả lại số lượng tồn kho vào hệ thống và chuyển trạng thái đơn hàng thành REFUNDED. Quyền truy cập: PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<OrderResponse>> approveRefund(
            @PathVariable Integer id,
            @RequestBody(required = false) OrderRefundActionRequest request) {
        OrderResponse response = orderService.approveRefund(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đã duyệt hoàn tiền thành công", response));
    }

    // Từ chối yêu cầu hoàn tiền (PO / Admin)
    @PostMapping("/{id}/refund-reject")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Từ chối yêu cầu hoàn tiền đơn hàng",
            description = "Từ chối yêu cầu hoàn tiền của khách hàng kèm theo lý do giải thích. Khôi phục lại trạng thái hợp lệ trước đó của đơn hàng. Quyền truy cập: PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<OrderResponse>> rejectRefund(
            @PathVariable Integer id,
            @RequestBody(required = false) OrderRefundActionRequest request) {
        OrderResponse response = orderService.rejectRefund(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối yêu cầu hoàn tiền", response));
    }

    // Lấy lịch sử chuyển trạng thái của đơn hàng (User / PO / Admin)
    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Lấy lịch sử thay đổi trạng thái của đơn hàng",
            description = "Truy xuất danh sách mốc thời gian, người thực hiện và trạng thái thay đổi trong suốt vòng đời của đơn hàng. Quyền truy cập: USER (chủ đơn), PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<List<OrderStatusHistoryResponse>>> getOrderHistory(
            @PathVariable Integer id) {
        List<OrderStatusHistoryResponse> history = orderService.getOrderHistory(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử đơn hàng thành công", history));
    }
}
