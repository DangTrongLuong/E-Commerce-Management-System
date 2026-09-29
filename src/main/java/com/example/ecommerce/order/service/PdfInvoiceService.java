package com.example.ecommerce.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfInvoiceService {

    private final JasperReportService jasperReportService;

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(Integer orderId) {
        return jasperReportService.generateJasperInvoicePdf(orderId);
    }


    /**
     * Save generated PDF to disk under /invoices folder
     */
    public String saveInvoiceToDisk(Integer orderId, byte[] pdfBytes) {
        File dir = new File("invoices");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File pdfFile = new File(dir, "invoice_order_" + orderId + ".pdf");
        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            fos.write(pdfBytes);
            log.info("Saved PDF invoice to disk: {}", pdfFile.getAbsolutePath());
            return pdfFile.getAbsolutePath();
        } catch (IOException e) {
            log.error("Failed to save PDF invoice to disk for order #{}", orderId, e);
            return null;
        }
    }
}
