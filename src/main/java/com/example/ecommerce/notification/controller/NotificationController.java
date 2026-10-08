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
@Tag(name = "Notifications", description = "User notification endpoints")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get my notifications")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách thông báo thành công", notificationService.getMyNotifications(page, size)));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đánh dấu thông báo đã đọc thành công", notificationService.markAsRead(id)));
    }
}
