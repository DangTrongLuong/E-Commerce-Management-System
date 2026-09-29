package com.example.ecommerce.payment.controller;

import com.example.ecommerce.payment.dto.VnPayIpnResponse;
import com.example.ecommerce.payment.service.VnPayService;
import com.example.ecommerce.common.util.VnPayUtils;
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
public class VnPayIpnController {
    VnPayService vnPayService;

    @GetMapping("/ipn")
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
