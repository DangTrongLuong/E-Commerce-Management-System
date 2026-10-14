package com.example.ecommerce.notification.controller;

import com.example.ecommerce.notification.entity.Notification;

import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.notification.dto.NotificationResponse;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Thông Báo Người Dùng (Notifications)", description = "Quản lý thông báo trong ứng dụng cho người dùng đã đăng nhập (Quyền: Đã đăng nhập - USER, PRODUCT_OWNER, ADMIN)")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách thông báo của người dùng hiện tại",
            description = "Truy vấn danh sách thông báo cá nhân (thông báo đơn hàng, duyệt vé, trạng thái thanh toán) có phân trang, sắp xếp theo thời gian mới nhất. Quyền truy cập: Đã đăng nhập."
    )
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách thông báo thành công", notificationService.getMyNotifications(page, size)));
    }

    @PatchMapping("/{id}/read")
    @Operation(
            summary = "Đánh dấu thông báo đã đọc",
            description = "Cập nhật trạng thái thông báo thành ĐÃ ĐỌC (isRead = true) theo mã ID thông báo. Quyền truy cập: Đã đăng nhập (chủ sở hữu thông báo)."
    )
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đánh dấu thông báo đã đọc thành công", notificationService.markAsRead(id)));
    }
}
