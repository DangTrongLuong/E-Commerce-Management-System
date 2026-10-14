package com.example.ecommerce.payment.controller;

import com.example.ecommerce.payment.dto.VnPayIpnResponse;
import com.example.ecommerce.payment.service.VnPayService;
import com.example.ecommerce.common.util.VnPayUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/vnpay")
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Tag(name = "Webhook VNPay IPN (Payment Callback)", description = "Xử lý thông báo tức thời (IPN) server-to-server từ cổng VNPay (Quyền: Công khai - Server VNPay)")
public class VnPayIpnController {
    VnPayService vnPayService;

    @GetMapping("/ipn")
    @Operation(
            summary = "Tiếp nhận webhook IPN từ máy chủ VNPay",
            description = "Điểm tiếp nhận thông báo kết quả thanh toán tức thời từ server VNPay. Xác minh checksum an toàn, cập nhật trạng thái đơn hàng sang CONFIRMED và kích hoạt email xác nhận thanh toán. Quyền truy cập: Công khai (Server-to-Server VNPay)."
    )
    public ResponseEntity<VnPayIpnResponse> ipn(HttpServletRequest request){
        Map<String, String> params = VnPayUtils.toSingleValueMap(request.getParameterMap());
        try {
            VnPayIpnResponse response = vnPayService.handleIpn(params);
            return ResponseEntity.ok(response);
        }catch (Exception ex){
            log.error("Unexpected error while processing VNPay IPN, params={}", params, ex);
            return ResponseEntity.ok(VnPayIpnResponse.unknownError());
        }
    }
}
