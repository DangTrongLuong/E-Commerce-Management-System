package com.example.ecommerce.report.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.report.dto.request.ReportExportRequest;
import com.example.ecommerce.report.dto.response.ReportDefinitionResponse;
import com.example.ecommerce.report.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Tag(name = "Báo Cáo Thống Kê (Reports)", description = "Quản lý và xuất báo cáo động với JasperReports (Quyền: ADMIN)")
public class ReportController {
    ReportService reportService;

    @GetMapping("/{reportCode}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Lấy định nghĩa tham số ParamSet của báo cáo",
            description = "Truy vấn danh sách tham số đầu vào (ParamSet), kiểu dữ liệu (DATE, STRING, INTEGER...), giá trị mặc định và trạng thái bắt buộc của mẫu báo cáo theo mã code. Quyền truy cập: ADMIN."
    )
    public ResponseEntity<ApiResponse<ReportDefinitionResponse>> getReportDefinition(
            @PathVariable String reportCode) {
        ReportDefinitionResponse reportDefinitionResponse = reportService.getReportDefinition(reportCode);
        return ResponseEntity
                .ok(ApiResponse.success("Lấy dịnh nghĩa tham số báo cáo thành công!", reportDefinitionResponse));
    }

    @PostMapping("/{reportCode}/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Xuất file báo cáo định dạng PDF hoặc Excel (XLSX)",
            description = "Tiếp nhận các giá trị tham số lọc từ client, tiến hành validate, nạp dữ liệu và xuất file báo cáo theo định dạng yêu cầu (PDF/XLSX). Quyền truy cập: ADMIN."
    )
    public ResponseEntity<byte[]> exportReport(
            @PathVariable String reportCode,
            @RequestBody ReportExportRequest request) {
        byte[] fileBytes = reportService.exportReport(reportCode, request);
        String fileName = reportService.getExportFileName(reportCode, request);
        MediaType mediaType = "XLSX".equalsIgnoreCase(request.getFormat())
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.APPLICATION_PDF;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(mediaType)
                .body(fileBytes);
    }
}
