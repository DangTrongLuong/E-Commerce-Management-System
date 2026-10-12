package com.example.ecommerce.payment.controller;

import com.example.ecommerce.common.util.VnPayUtils;

import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.payment.dto.PaymentResponse;
import com.example.ecommerce.payment.dto.VnPayReturnResult;
import com.example.ecommerce.payment.service.VnPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/vnpay")
@FieldDefaults(makeFinal = true,level = AccessLevel.PRIVATE)
@Tag(name = "Payment", description = "VNPay payment endpoints")
public class PaymentController {
    VnPayService vnPayService;

    @GetMapping("/return")
    @Operation(summary = "Trình xử lý phản hồi VNPay (display only, public)")
    public ResponseEntity<ApiResponse<VnPayReturnResult>> vnPayReturn(HttpServletRequest request) {
        Map<String, String> params = VnPayUtils
                .toSingleValueMap(request.getParameterMap());
        VnPayReturnResult result = vnPayService.handleResult(params);
        return ResponseEntity.ok(ApiResponse.success("Kết quả thanh toán VNPay đã được xử lý thành công", result));
    }

    @GetMapping("/history/{orderId}")
    @PreAuthorize("hasAnyRole('USER', 'PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Lấy lịch sử các lần thử thanh toán của một đơn hàng (User/PO/Admin)")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getHistory(@PathVariable Integer orderId) {
        List<PaymentResponse> history = vnPayService.getPaymentHistory(orderId);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử thanh toán thành công", history));
    }

}
