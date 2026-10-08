package com.example.ecommerce.common.exception;

import com.example.ecommerce.common.dto.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExeptionHandler {

    // 400 BAD REQUEST & VALIDATION
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("field", err.getField());
                    m.put("message", err.getDefaultMessage());
                    return m;
                })
                .toList();
        log.warn("Validation error: {}", details);
        ApiResponse<Object> body = ApiResponse.error("VALIDATION_ERROR", "Validation failed", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(BadRequestExeption.class)
    public ResponseEntity<ApiResponse<Object>> handleCustomBadRequest(BadRequestExeption ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(QuantityLimitExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleQuantityLimitExceeded(QuantityLimitExceededException ex) {
        log.warn("Quantity limit exceeded: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "QUANTITY_LIMIT_EXCEEDED", ex.getMessage());
    }

    @ExceptionHandler(ProductInactiveException.class)
    public ResponseEntity<ApiResponse<Object>> handleProductInactive(ProductInactiveException ex) {
        log.warn("Product inactive: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "PRODUCT_INACTIVE", ex.getMessage());
    }

    @ExceptionHandler(MixedOwnerOrderException.class)
    public ResponseEntity<ApiResponse<Object>> handleMixedOwner(MixedOwnerOrderException ex) {
        log.warn("Mixed owner order error: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "MIXED_OWNER_ORDER", ex.getMessage());
    }

    // 401 UNAUTHORIZED
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ApiResponse<Object>> handleTokenExpired(TokenExpiredException ex) {
        return build(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", ex.getMessage());
    }

    // 403 FORBIDDEN
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank()) ? ex.getMessage() : "Access denied";
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccountLocked(AccountLockedException ex) {
        return build(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", ex.getMessage());
    }

    @ExceptionHandler(SelfApprovalNotAllowedException.class)
    public ResponseEntity<ApiResponse<Object>> handleSelfApproval(SelfApprovalNotAllowedException ex) {
        return build(HttpStatus.FORBIDDEN, "SELF_APPROVAL_NOT_ALLOWED", ex.getMessage());
    }

    @ExceptionHandler(AccountUnverifiedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccountUnverified(AccountUnverifiedException ex) {
        log.warn("Account unverified attempt: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "ACCOUNT_UNVERIFIED", ex.getMessage());
    }

    // 404 NOT FOUND
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    // 409 CONFLICT
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<Object>> handleInsufficientStock(InsufficientStockException ex) {
        log.warn("Insufficient stock: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidOrderStatus(InvalidOrderStatusException ex) {
        log.warn("Invalid order status transition: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS", ex.getMessage());
    }

    @ExceptionHandler(InvalidTicketStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidTicketStatus(InvalidTicketStatusException ex) {
        log.warn("Invalid ticket status transition: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "INVALID_TICKET_STATUS", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Concurrent update conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "Đơn hàng hoặc dữ liệu đã bị chỉnh sửa đồng thời bởi một giao dịch khác. Vui lòng thử lại.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage());
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("UK_ORDER_ITEM_ORDER_PRODUCT") || msg.contains("order_items"))) {
            return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                    "Sản phẩm này đã được thêm vào đơn hàng bởi một giao dịch khác cùng thời điểm.");
        }
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY", "Data constraint violation");
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleConflict(ConflictException ex) {
        log.warn("Business conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(OrderNotEditableException.class)
    public ResponseEntity<ApiResponse<Object>> handleOrderNotEditable(OrderNotEditableException ex) {
        log.warn("Order not editable: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "ORDER_NOT_EDITABLE", ex.getMessage());
    }

    // 500 INTERNAL ERROR
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        log.error("Unhandled server error: ", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ApiResponse<Object>> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }

}
