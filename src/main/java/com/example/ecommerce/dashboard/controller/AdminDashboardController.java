package com.example.ecommerce.dashboard.controller;

import com.example.ecommerce.dashboard.dto.DashboardResponse;
import com.example.ecommerce.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard Thống Kê (Dashboard)", description = "Tổng quan số liệu kinh doanh chạy song song (Quyền: ADMIN)")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy dữ liệu thống kê Dashboard chạy song song", description = "Chạy 4 phần song song trên dashboardExecutor (revenueToday, orderCountByStatus, top5Products, lowStockProducts)")
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "parallel") String mode) {

        if (date == null) {
            date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        }
        log.info("Received request for admin dashboard. date={}, mode={}", date, mode);
        DashboardResponse response = dashboardService.getDashboard(date, mode);
        return ResponseEntity.ok(response);
    }
}
