package com.example.ecommerce.order.service;

import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.entity.OrderItem;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.common.exception.BadRequestExeption;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.order.repository.OrderRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class JasperReportService {

    private final OrderRepository orderRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getInstance(new Locale("vi", "VN"));

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JasperItemDto {
        private String productName;
        private Integer quantity;
        private String unitPrice;
        private String subtotal;
    }

    @Transactional(readOnly = true)
    public byte[] generateJasperInvoicePdf(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderId));

        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestExeption(
                    "Hóa đơn chỉ khả dụng cho đơn hàng đã được phê duyệt (CONFIRMED, PROCESSING, COMPLETED). Trạng thái hiện tại: "
                            + order.getStatus());
        }

        try {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("orderId", String.valueOf(order.getId()));
            parameters.put("customerName", order.getCustomer() != null ? order.getCustomer().getName() : "Khách hàng");
            parameters.put("customerPhone",
                    (order.getCustomer() != null && order.getCustomer().getPhone() != null)
                            ? order.getCustomer().getPhone()
                            : "N/A");
            parameters.put("status", order.getStatus() != null ? order.getStatus().name() : "N/A");
            parameters.put("createdDate",
                    order.getCreatedAt() != null ? order.getCreatedAt().format(DATE_FORMATTER) : "N/A");
            parameters.put("totalAmount", formatCurrency(order.getTotalAmount()) + " VNĐ");

            List<JasperItemDto> itemsList = new ArrayList<>();
            if (order.getOrderItems() != null) {
                for (OrderItem item : order.getOrderItems()) {
                    BigDecimal subtotal = item.getSubTotal() != null ? item.getSubTotal()
                            : item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                    itemsList.add(JasperItemDto.builder()
                            .productName(item.getProduct() != null ? item.getProduct().getName()
                                    : "Sản phẩm #" + item.getId())
                            .quantity(item.getQuantity())
                            .unitPrice(formatCurrency(item.getUnitPrice()))
                            .subtotal(formatCurrency(subtotal))
                            .build());
                }
            }
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(itemsList);

            InputStream jrxmlInput = new ClassPathResource("jasper/invoice.jrxml").getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlInput);

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);

            log.info("Successfully generated JasperReport PDF for order #{}", orderId);
            return pdfBytes;
        } catch (Exception e) {
            log.error("Error generating JasperReport PDF for order #{}", orderId, e);
            throw new RuntimeException("Failed to generate JasperReport PDF: " + e.getMessage(), e);
        }
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null)
            return "0";
        return CURRENCY_FORMATTER.format(amount);
    }
}
