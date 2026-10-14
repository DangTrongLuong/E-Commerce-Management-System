package com.example.ecommerce.ticket.controller;

import com.example.ecommerce.order.dto.OrderItemRequest;
import com.example.ecommerce.ticket.dto.TicketActionRequest;
import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.ticket.dto.TicketHistoryResponse;
import com.example.ecommerce.ticket.dto.TicketResponse;
import com.example.ecommerce.ticket.enums.TicketStatus;
import com.example.ecommerce.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Vé Phê Duyệt Mua Hàng (Tickets)", description = "Quy trình phê duyệt đơn hàng mua sắm qua Purchase Tickets (Quyền: USER, PRODUCT_OWNER, ADMIN)")
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Lấy danh sách vé phê duyệt của người dùng hiện tại",
            description = "Truy xuất danh sách các vé mua hàng do chính người dùng gửi, hỗ trợ lọc theo trạng thái vé (SUBMITTED, APPROVED, REJECTED, RETURNED) và phân trang. Quyền truy cập: USER."
    )
    public ResponseEntity<ApiResponse<PageResponse<TicketResponse>>> getMyTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách vé đã gửi thành công", ticketService.getMyTickets(status, page, size)));
    }

    @GetMapping("/inbox")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Lấy danh sách hộp thư vé chờ phê duyệt",
            description = "Tra cứu các vé mua hàng đang chờ xử lý liên quan đến sản phẩm do PO phụ trách hoặc toàn bộ hệ thống dành cho ADMIN. Quyền truy cập: PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<PageResponse<TicketResponse>>> getInboxTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách vé chờ xử lý thành công", ticketService.getInboxTickets(status, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Lấy thông tin chi tiết vé mua hàng theo ID",
            description = "Xem chi tiết nội dung vé bao gồm đơn hàng gắn liền, danh sách sản phẩm yêu cầu, tổng giá trị và trạng thái hiện tại. Quyền truy cập: USER (người tạo), PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<TicketResponse>> getTicketById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin chi tiết vé thành công", ticketService.getTicketById(id)));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Lấy thông tin vé mua hàng theo mã đơn hàng",
            description = "Tra cứu thông tin vé phê duyệt gắn liền với một đơn hàng cụ thể theo orderId. Quyền truy cập: USER (chủ đơn), PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<TicketResponse>> getTicketByOrderId(@PathVariable("orderId") Long orderId) {
        TicketResponse res = ticketService.getTicketByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin vé theo đơn hàng thành công", res));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Xem lịch sử thay đổi và nhật ký xử lý vé",
            description = "Truy xuất toàn bộ nhật ký phê duyệt (audit trail) bao gồm người thực hiện, thời gian, hành động (duyệt/từ chối/trả lại) và phản hồi ghi chú. Quyền truy cập: USER, PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<List<TicketHistoryResponse>>> getTicketHistory(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử vé thành công", ticketService.getTicketHistory(id)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Phê duyệt vé mua hàng",
            description = "Chấp thuận yêu cầu mua hàng trong vé, chuyển trạng thái vé sang APPROVED và mở khóa trạng thái cho đơn hàng tiếp tục xử lý. Quyền truy cập: PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<TicketResponse>> approveTicket(
            @PathVariable("id") Long id,
            @RequestBody(required = false) TicketActionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Phê duyệt vé thành công", ticketService.approveTicket(id, request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Từ chối vé mua hàng kèm lý do",
            description = "Từ chối yêu cầu mua hàng trong vé, bắt buộc phải cung cấp lý do từ chối (comment). Vé sẽ chuyển sang trạng thái REJECTED. Quyền truy cập: PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<TicketResponse>> rejectTicket(
            @PathVariable("id") Long id,
            @Valid @RequestBody TicketActionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Từ chối vé thành công", ticketService.rejectTicket(id, request)));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Yêu cầu người dùng chỉnh sửa lại nội dung vé",
            description = "Trả vé lại cho người gửi kèm ghi chú yêu cầu điều chỉnh danh mục hoặc số lượng mặt hàng (bắt buộc nhập comment). Vé chuyển sang trạng thái RETURNED. Quyền truy cập: PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<TicketResponse>> returnTicket(
            @PathVariable("id") Long id,
            @Valid @RequestBody TicketActionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Yêu cầu chỉnh sửa vé thành công", ticketService.returnTicket(id, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Chỉnh sửa danh sách sản phẩm trong vé bị trả lại",
            description = "Người dùng cập nhật lại số lượng và danh sách mặt hàng theo phản hồi của người duyệt khi vé đang ở trạng thái RETURNED. Quyền truy cập: USER."
    )
    public ResponseEntity<ApiResponse<TicketResponse>> updateTicketItems(
            @PathVariable("id") Long id,
            @Valid @RequestBody List<OrderItemRequest> items
    ) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật danh sách sản phẩm trong vé thành công", ticketService.updateTicketItems(id, items)));
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Gửi lại vé sau khi đã chỉnh sửa",
            description = "Chuyển trạng thái vé từ RETURNED trở lại SUBMITTED để người duyệt tiến hành thẩm định lại yêu cầu mua hàng. Quyền truy cập: USER."
    )
    public ResponseEntity<ApiResponse<TicketResponse>> resubmitTicket(
            @PathVariable("id") Long id,
            @RequestBody(required = false) TicketActionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Gửi lại vé phê duyệt thành công", ticketService.resubmitTicket(id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(
            summary = "Hủy bỏ vé mua hàng",
            description = "Người gửi hoặc Quản trị viên hủy bỏ vé yêu cầu mua hàng. Đơn hàng liên quan sẽ bị hủy bỏ tương ứng. Quyền truy cập: USER (người tạo vé), ADMIN."
    )
    public ResponseEntity<ApiResponse<TicketResponse>> cancelTicket(
            @PathVariable("id") Long id,
            @RequestBody(required = false) TicketActionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Hủy vé phê duyệt thành công", ticketService.cancelTicket(id, request)));
    }
}
