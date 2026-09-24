package com.example.ecommerce.controller;

import com.example.ecommerce.service.JasperReportService;
import com.example.ecommerce.service.OrderService;
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
@Tag(name = "JasperReport", description = "Endpoints for JasperReports PDF export")
public class JasperReportController {

    JasperReportService jasperReportService;
    OrderService orderService;

    @GetMapping(value = "/orders/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Export basic invoice PDF using JasperReports engine")
    public ResponseEntity<byte[]> exportJasperInvoicePdf(@PathVariable Integer id) {
        orderService.getOrderById(id); // Check access authority
        byte[] pdfBytes = jasperReportService.generateJasperInvoicePdf(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"jasper_invoice_order_" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
