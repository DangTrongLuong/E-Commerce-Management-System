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
@Tag(name = "Thanh Toán VNPay (Payments)", description = "Xử lý kết quả giao dịch và tra cứu lịch sử thanh toán qua cổng VNPay (Quyền: Công khai, USER, PRODUCT_OWNER, ADMIN)")
public class PaymentController {
    VnPayService vnPayService;

    @GetMapping("/return")
    @Operation(
            summary = "Xử lý kết quả điều hướng giao dịch VNPay (Return URL)",
            description = "Điểm tiếp nhận phản hồi từ cổng VNPay sau khi khách hàng hoàn tất giao dịch. Xác thực chữ ký số Secure Hash và trả về trạng thái giao dịch cho giao diện Frontend. Quyền truy cập: Công khai."
    )
    public ResponseEntity<ApiResponse<VnPayReturnResult>> vnPayReturn(HttpServletRequest request) {
        Map<String, String> params = VnPayUtils
                .toSingleValueMap(request.getParameterMap());
        VnPayReturnResult result = vnPayService.handleResult(params);
        return ResponseEntity.ok(ApiResponse.success("Kết quả thanh toán VNPay đã được xử lý thành công", result));
    }

    @GetMapping("/history/{orderId}")
    @PreAuthorize("hasAnyRole('USER', 'PRODUCT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Tra cứu lịch sử các lần thanh toán của đơn hàng",
            description = "Truy vấn toàn bộ các lượt thử thanh toán (mã giao dịch, ngân hàng, số tiền, trạng thái thành công/thất bại) gắn liền với orderId. Quyền truy cập: USER (chủ đơn), PRODUCT_OWNER, ADMIN."
    )
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getHistory(@PathVariable Integer orderId) {
        List<PaymentResponse> history = vnPayService.getPaymentHistory(orderId);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử thanh toán thành công", history));
    }
}
