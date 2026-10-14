package com.example.ecommerce.order.controller;

import com.example.ecommerce.order.service.JasperReportService;
import com.example.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/jasper")
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Tag(name = "Hóa Đơn JasperReports (Invoices)", description = "Xuất hóa đơn bán hàng định dạng PDF qua JasperReports (Quyền: Đã đăng nhập - USER, PRODUCT_OWNER, ADMIN)")
public class JasperReportController {

    JasperReportService jasperReportService;
    OrderService orderService;

    @GetMapping(value = "/orders/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Xuất hóa đơn bán hàng dạng PDF qua JasperReports",
            description = "Biên dịch mẫu hóa đơn và xuất file PDF chứa đầy đủ danh mục sản phẩm, đơn giá, số lượng và tổng thanh toán của đơn hàng theo orderId. Quyền truy cập: USER (chủ đơn), PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<byte[]> exportJasperInvoicePdf(@PathVariable Integer id) {
        orderService.getOrderById(id); // Check access authority
        byte[] pdfBytes = jasperReportService.generateJasperInvoicePdf(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"jasper_invoice_order_" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
