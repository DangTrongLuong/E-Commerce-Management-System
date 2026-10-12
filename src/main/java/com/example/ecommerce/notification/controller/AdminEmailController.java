package com.example.ecommerce.notification.controller;

import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.notification.dto.EmailOutboxResponse;
import com.example.ecommerce.notification.enums.EmailOutboxStatus;
import com.example.ecommerce.notification.service.EmailOutboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/emails")
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Emails", description = "Quản lý hàng đợi email outbox và gửi lại email lỗi (ADMIN)")
public class AdminEmailController {

    EmailOutboxService emailOutboxService;

    @GetMapping
    @Operation(summary = "Lấy danh sách email outbox (hỗ trợ lọc status=FAILED, phân trang)")
    public ResponseEntity<ApiResponse<PageResponse<EmailOutboxResponse>>> getEmails(
            @RequestParam(required = false) EmailOutboxStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<EmailOutboxResponse> response = emailOutboxService.getEmails(
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách email outbox thành công", response));
    }

    @PostMapping("/{id}/resend")
    @Operation(summary = "Gửi lại email lỗi (đặt lại STATUS = PENDING, ATTEMPTS = 0)")
    public ResponseEntity<ApiResponse<EmailOutboxResponse>> resendEmail(@PathVariable Long id) {
        EmailOutboxResponse response = emailOutboxService.resendEmail(id);
        return ResponseEntity.ok(ApiResponse.success("Đã đưa email vào hàng đợi gửi lại thành công", response));
    }
}
