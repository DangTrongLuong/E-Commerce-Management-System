package com.example.ecommerce.common.exception;

import com.example.ecommerce.payment.controller.PaymentController;
import com.example.ecommerce.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = PaymentController.class)
public class PaymentExceptionHandler {

    @ExceptionHandler(InvalidVnPaySignatureException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSignature(InvalidVnPaySignatureException ex) {
        log.error("Invalid VNPay signature detected: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_SIGNATURE", ex.getMessage()));
    }
}
