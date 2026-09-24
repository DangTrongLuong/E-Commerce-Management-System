package com.example.ecommerce.service;

import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.Order;
import com.example.ecommerce.entity.OrderItem;
import com.example.ecommerce.entity.PurchaseTicket;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.exception.BadRequestExeption;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.PurchaseTicketRepository;
import com.example.ecommerce.util.SecurityUtils;
import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfInvoiceService {

    private final OrderRepository orderRepository;
    private final PurchaseTicketRepository ticketRepository;
    private final SecurityUtils securityUtils;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getInstance(new Locale("vi", "VN"));

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        // Check order status: invoice can only be generated for CONFIRMED, PROCESSING, COMPLETED
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestExeption("Hóa đơn chỉ khả dụng cho đơn hàng đã được phê duyệt (CONFIRMED, PROCESSING, COMPLETED). Trạng thái hiện tại: " + order.getStatus());
        }

        PurchaseTicket ticket = ticketRepository.findByOrderId(Long.valueOf(orderId)).orElse(null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Setup UTF-8 BaseFont for full Vietnamese support
            BaseFont baseFont = getVietnameseBaseFont();

            // Monochrome (Black & White) Font Definitions
            Font titleFont = new Font(baseFont, 18, Font.BOLD, Color.BLACK);
            Font subtitleFont = new Font(baseFont, 11, Font.BOLD, Color.BLACK);
            Font boldFont = new Font(baseFont, 10, Font.BOLD, Color.BLACK);
            Font regularFont = new Font(baseFont, 10, Font.NORMAL, Color.BLACK);
            Font smallFont = new Font(baseFont, 8, Font.NORMAL, Color.DARK_GRAY);
            Font priceFont = new Font(baseFont, 13, Font.BOLD, Color.BLACK);

            // 1. Header Banner (Clean Monochrome Layout)
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{60, 40});

            PdfPCell leftHeader = new PdfPCell(new Phrase("HÓA ĐƠN MUA HÀNG", titleFont));
            leftHeader.setBorder(Rectangle.NO_BORDER);
            leftHeader.setVerticalAlignment(Element.ALIGN_MIDDLE);
            headerTable.addCell(leftHeader);

            String approvedAtStr = (ticket != null && ticket.getDecidedAt() != null)
                    ? ticket.getDecidedAt().format(DATE_FORMATTER)
                    : (order.getCreatedAt() != null ? order.getCreatedAt().format(DATE_FORMATTER) : "N/A");

            String metaText = "Mã đơn hàng: #" + order.getId() + "\n" +
                              "Ngày tạo: " + (order.getCreatedAt() != null ? order.getCreatedAt().format(DATE_FORMATTER) : "N/A") + "\n" +
                              "Ngày duyệt: " + approvedAtStr + "\n" +
                              "Trạng thái: " + order.getStatus().name();

            PdfPCell rightHeader = new PdfPCell(new Phrase(metaText, smallFont));
            rightHeader.setBorder(Rectangle.NO_BORDER);
            rightHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);
            headerTable.addCell(rightHeader);

            document.add(headerTable);
            document.add(new Paragraph(" ")); // spacing

            // 2. Info Cards (Buyer & Seller) - Black & White Borders
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setWidths(new float[]{50, 50});

            Customer customer = order.getCustomer();
            String buyerName = customer != null ? customer.getName() : "N/A";
            String buyerEmail = customer != null ? customer.getEmail() : "N/A";
            String buyerPhone = (customer != null && customer.getPhone() != null) ? customer.getPhone() : "N/A";

            PdfPCell buyerCell = new PdfPCell();
            buyerCell.setBackgroundColor(new Color(250, 250, 250)); // Light Gray tint
            buyerCell.setPadding(8);
            buyerCell.setBorderColor(new Color(200, 200, 200));
            buyerCell.addElement(new Paragraph("THÔNG TIN NGƯỜI MUA (BUYER):", subtitleFont));
            buyerCell.addElement(new Paragraph("Họ tên: " + buyerName, regularFont));
            buyerCell.addElement(new Paragraph("Email: " + buyerEmail, regularFont));
            buyerCell.addElement(new Paragraph("Số điện thoại: " + buyerPhone, regularFont));
            infoTable.addCell(buyerCell);

            String sellerEmail = (ticket != null && ticket.getApprover() != null)
                    ? ticket.getApprover().getEmail()
                    : "Hệ thống E-Commerce Store";

            PdfPCell sellerCell = new PdfPCell();
            sellerCell.setBackgroundColor(new Color(250, 250, 250));
            sellerCell.setPadding(8);
            sellerCell.setBorderColor(new Color(200, 200, 200));
            sellerCell.addElement(new Paragraph("THÔNG TIN NGƯỜI BÁN (SELLER):", subtitleFont));
            sellerCell.addElement(new Paragraph("Đơn vị: E-Commerce Store System", regularFont));
            sellerCell.addElement(new Paragraph("Email Người duyệt / PO: " + sellerEmail, regularFont));
            sellerCell.addElement(new Paragraph("Mã phiếu duyệt: " + (ticket != null ? "#" + ticket.getId() : "Tự động phê duyệt"), regularFont));
            infoTable.addCell(sellerCell);

            document.add(infoTable);
            document.add(new Paragraph(" "));

            // 3. Products Table - Grayscale Header & Subtle Rows
            PdfPTable itemTable = new PdfPTable(5);
            itemTable.setWidthPercentage(100);
            itemTable.setWidths(new float[]{10, 15, 35, 15, 25});

            // Table Headers (Monochrome Dark/Light Gray)
            String[] headers = {"STT", "Mã SP", "Tên Sản Phẩm", "Số Lượng", "Thành Tiền (VNĐ)"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, boldFont));
                cell.setBackgroundColor(new Color(230, 230, 230)); // Grayscale header
                cell.setPadding(6);
                cell.setBorderColor(new Color(180, 180, 180));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                itemTable.addCell(cell);
            }

            // Table Rows
            int index = 1;
            for (OrderItem item : order.getOrderItems()) {
                Color rowBg = (index % 2 == 0) ? Color.WHITE : new Color(248, 248, 248);

                addCell(itemTable, String.valueOf(index++), regularFont, Element.ALIGN_CENTER, rowBg);
                addCell(itemTable, "#" + item.getProduct().getId(), regularFont, Element.ALIGN_CENTER, rowBg);
                addCell(itemTable, item.getProduct().getName(), regularFont, Element.ALIGN_LEFT, rowBg);
                addCell(itemTable, String.valueOf(item.getQuantity()), regularFont, Element.ALIGN_CENTER, rowBg);

                BigDecimal subtotal = item.getSubTotal() != null ? item.getSubTotal() : item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                addCell(itemTable, formatCurrency(subtotal), regularFont, Element.ALIGN_RIGHT, rowBg);
            }

            document.add(itemTable);
            document.add(new Paragraph(" "));

            // 4. Summary Total Box - Monochrome
            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(100);
            totalTable.setWidths(new float[]{55, 45});

            PdfPCell emptyCell = new PdfPCell(new Phrase(" ", regularFont));
            emptyCell.setBorder(Rectangle.NO_BORDER);
            totalTable.addCell(emptyCell);

            PdfPCell totalCell = new PdfPCell();
            totalCell.setBackgroundColor(new Color(245, 245, 245));
            totalCell.setPadding(8);
            totalCell.setBorderColor(new Color(180, 180, 180));
            totalCell.addElement(new Paragraph("TỔNG CỘNG TIỀN THANH TOÁN:", boldFont));
            totalCell.addElement(new Paragraph(formatCurrency(order.getTotalAmount()) + " VNĐ", priceFont));
            totalTable.addCell(totalCell);

            document.add(totalTable);
            document.add(new Paragraph(" "));

            // 5. Footer Note
            Paragraph footer = new Paragraph("Cảm ơn quý khách đã mua hàng tại E-Commerce Store System!\nHóa đơn được tạo tự động từ hệ thống khi đơn hàng được phê duyệt.", smallFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            log.info("Successfully generated monochrome PDF invoice for order #{}", orderId);
        } catch (Exception e) {
            log.error("Failed to generate PDF invoice for order #{}", orderId, e);
            throw new RuntimeException("Could not generate PDF invoice: " + e.getMessage(), e);
        }

        return out.toByteArray();
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

    private BaseFont getVietnameseBaseFont() {
        String[] fontCandidates = {
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/times.ttf",
            "C:/Windows/Fonts/tahoma.ttf",
            "C:/Windows/Fonts/segoeui.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/ttf-dejavu/DejaVuSans.ttf",
            "/System/Library/Fonts/Supplemental/Arial.ttf"
        };
        for (String path : fontCandidates) {
            File fontFile = new File(path);
            if (fontFile.exists()) {
                try {
                    return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                } catch (Exception e) {
                    log.warn("Could not load TTF font from {}: {}", path, e.getMessage());
                }
            }
        }
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new RuntimeException("Fallback base font creation failed", e);
        }
    }

    private void addCell(PdfPTable table, String text, Font font, int alignment, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(new Color(220, 220, 220));
        table.addCell(cell);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0";
        return CURRENCY_FORMATTER.format(amount);
    }
}
