package com.example.ecommerce.payment.service;

import com.example.ecommerce.user.enums.Role;

import com.example.ecommerce.common.config.VnPayProperties;
import com.example.ecommerce.payment.dto.PaymentCreationRequest;
import com.example.ecommerce.payment.dto.PaymentResponse;
import com.example.ecommerce.payment.dto.PaymentUrlResponse;
import com.example.ecommerce.payment.dto.VnPayIpnResponse;
import com.example.ecommerce.payment.dto.VnPayReturnResult;
import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.payment.entity.PaymentTransaction;
import com.example.ecommerce.order.enums.OrderPaymentStatus;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.payment.enums.PaymentStatus;
import com.example.ecommerce.common.exception.ConflictException;
import com.example.ecommerce.common.exception.InvalidVnPaySignatureException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.payment.mapper.PaymentMapper;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.payment.repository.PaymentTransactionRepository;
import com.example.ecommerce.common.util.SecurityUtils;
import com.example.ecommerce.common.util.VnPayUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class VnPayService {
    VnPayProperties vnPayProperties;
    PaymentTransactionRepository paymentRepository;
    OrderRepository orderRepository;
    PaymentMapper paymentMapper;
    SecurityUtils securityUtils;

    @Transactional
    public PaymentUrlResponse createPaymentUrl(
            PaymentCreationRequest paymentCreationRequest,
            HttpServletRequest httpServletRequest) {
        Order order = orderRepository.findById(paymentCreationRequest.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy đơn hàng với mã: " + paymentCreationRequest.getOrderId()));

        securityUtils.verifyUserOrAdmin(Long.valueOf(order.getCustomer().getId()));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ConflictException(
                    "Order #" + order.getId() + " chưa thể thanh toán. Trạng thái hiện tại: "
                            + order.getStatus() + ". Yêu cầu: " + OrderStatus.CONFIRMED);
        }

        String txnRef = buildTxnRef(order.getId());
        String createDate = VnPayUtils.now();
        String expireDate = VnPayUtils.plusMinutes((vnPayProperties.getExpireMinutes()));
        String orderInfo = "Thanh toan don hang #" + order.getId();

        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .orderId(order.getId())
                .vnpTxnRef(txnRef)
                .amount(order.getTotalAmount())
                .bankCode(paymentCreationRequest.getBankCode())
                .orderInfo(orderInfo)
                .ipAddress(VnPayUtils.getClientIpAddress(httpServletRequest))
                .status(PaymentStatus.PENDING)
                .build();

        paymentRepository.save(paymentTransaction);

        Map<String, String> params = new HashMap<>();

        params.put("vnp_Version", vnPayProperties.getApiVersion());
        params.put("vnp_Command", vnPayProperties.getCommand());
        params.put("vnp_TmnCode", vnPayProperties.getTmnCode());
        params.put("vnp_Amount", order.getTotalAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.UNNECESSARY)
                .toBigInteger().toString());
        params.put("vnp_CurrCode", vnPayProperties.getCurrencyCode());
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderType", vnPayProperties.getOrderType());
        params.put("vnp_Locale",
                (paymentCreationRequest.getLocale() != null && !paymentCreationRequest.getLocale().isBlank())
                        ? paymentCreationRequest.getLocale()
                        : vnPayProperties.getDefaultLocale());
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_ReturnUrl", vnPayProperties.getReturnUrl());
        params.put("vnp_IpAddr", paymentTransaction.getIpAddress());
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_ExpireDate", expireDate);

        if (paymentCreationRequest.getBankCode() != null && !paymentCreationRequest.getBankCode().isBlank()) {
            params.put("vnp_BankCode", paymentCreationRequest.getBankCode());
        }

        VnPayUtils.BuiltQuery build = VnPayUtils.buildQuery(params);

        String secureHash = VnPayUtils.hmacSHA512(vnPayProperties.getHashSecret(), build.hashData());
        String paymentUrl = vnPayProperties.getPayUrl() + "?" + build.queryString() + "&vnp_SecureHash=" + secureHash;
        paymentTransaction.setSecureHash(secureHash);
        paymentRepository.save(paymentTransaction);

        LocalDateTime expireAt = LocalDateTime.parse(expireDate, VnPayUtils.VNP_DATE_FORMAT);

        return PaymentUrlResponse.builder()
                .paymentUrl(paymentUrl)
                .txnRef(txnRef)
                .amount(order.getTotalAmount())
                .expireAt(expireAt)
                .build();

    }

    @Transactional
    public VnPayIpnResponse handleIpn(Map<String, String> vnpParams) {
        Map<String, String> params = new HashMap<>(vnpParams);

        String receivedHash = params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");

        String hashData = VnPayUtils.buildHashData(params);
        String calculatedHash = VnPayUtils.hmacSHA512(vnPayProperties.getHashSecret(), hashData);

        if (!calculatedHash.equalsIgnoreCase(receivedHash)) {
            log.warn("VNPay IPN invalid signature for txnRef={}", params.get("vnp_TxnRef"));
            return VnPayIpnResponse.invalidSignature();
        }

        String txnRef = params.get("vnp_TxnRef");
        Optional<PaymentTransaction> optionalPaymentTransaction = paymentRepository.findByVnpTxnRefForUpdate(txnRef);

        if (optionalPaymentTransaction.isEmpty()) {
            return VnPayIpnResponse.orderNotFound();
        }

        PaymentTransaction paymentTransaction = optionalPaymentTransaction.get();

        if (paymentTransaction.isIpnConfirmed()) {
            return VnPayIpnResponse.alreadyConfirmed();
        }

        BigDecimal receivedAmount = new BigDecimal(params.get("vnp_Amount")).divide(BigDecimal.valueOf(100));
        if (receivedAmount.compareTo(paymentTransaction.getAmount()) != 0) {
            log.error("VNPay IPN amount mismatch. txnRef={}, expected={}, received={}",
                    txnRef, paymentTransaction.getAmount(), receivedAmount);
            return VnPayIpnResponse.invalidAmount();
        }

        String responseCode = params.get("vnp_ResponseCode");
        boolean isSuccess = "00".equals(responseCode);

        paymentTransaction.setResponseCode(responseCode);
        paymentTransaction.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        paymentTransaction.setPayDate(params.get("vnp_PayDate"));
        paymentTransaction.setStatus(isSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        paymentTransaction.setIpnConfirmed(true);

        paymentRepository.save(paymentTransaction);

        Order order = orderRepository.findById(paymentTransaction.getOrderId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã ID: " + paymentTransaction.getOrderId()));

        if (isSuccess) {
            order.setPaymentStatus(OrderPaymentStatus.PAID);
            order.setStatus(OrderStatus.PROCESSING);
        } else {
            order.setPaymentStatus(OrderPaymentStatus.FAILED);
        }
        orderRepository.save(order);

        log.info("VNPay IPN processed. txnRef={}, orderId={}, status={}",
                txnRef, paymentTransaction.getOrderId(), paymentTransaction.getStatus());

        return VnPayIpnResponse.success();
    }

    @Transactional
    public VnPayReturnResult handleResult(Map<String, String> vnpParams) {
        Map<String, String> params = new HashMap<>(vnpParams);
        String receivedHash = params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");

        String hashData = VnPayUtils.buildHashData(params);
        String calculatedHash = VnPayUtils.hmacSHA512(vnPayProperties.getHashSecret(), hashData);
        if (!calculatedHash.equalsIgnoreCase(receivedHash)) {
            throw new InvalidVnPaySignatureException(
                    "Chữ ký Return URL không hợp lệ cho mã giao dịch " + params.get("vnp_TxnRef"));
        }

        String txnRef = params.get("vnp_TxnRef");
        PaymentTransaction paymentTransaction = paymentRepository.findByVnpTxnRef(txnRef)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch thanh toán với mã: " + txnRef));

        String responseCode = params.get("vnp_ResponseCode");
        boolean isSuccess = "00".equals(responseCode);

        // Fallback status update if IPN has not reached localhost
        if (!paymentTransaction.isIpnConfirmed()) {
            paymentTransaction.setResponseCode(responseCode);
            paymentTransaction.setVnpTransactionNo(params.get("vnp_TransactionNo"));
            paymentTransaction.setPayDate(params.get("vnp_PayDate"));
            paymentTransaction.setStatus(isSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
            paymentTransaction.setIpnConfirmed(true);
            paymentRepository.save(paymentTransaction);

            Order order = orderRepository.findById(paymentTransaction.getOrderId()).orElse(null);
            if (order != null) {
                if (isSuccess) {
                    order.setPaymentStatus(OrderPaymentStatus.PAID);
                    order.setStatus(OrderStatus.PROCESSING);
                } else {
                    order.setPaymentStatus(OrderPaymentStatus.FAILED);
                }
                orderRepository.save(order);
            }
        }

        String message = isSuccess
                ? "Giao dịch thành công"
                : "Giao dịch không thành công (Mã lỗi: " + responseCode + ")";

        return VnPayReturnResult.builder()
                .orderId(paymentTransaction.getOrderId())
                .vnpTxnRef(txnRef)
                .responseCode(responseCode)
                .message(message)
                .amount(paymentTransaction.getAmount())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentHistory(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã ID: " + orderId));

        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            boolean isCustomer = currentUser.getRole() == Role.USER && order.getCustomer() != null && currentUser.getCustomer() != null && order.getCustomer().getId() == currentUser.getCustomer().getId();
            boolean isOwner = currentUser.getRole() == Role.PRODUCT_OWNER && order.getOrderItems().stream()
                    .anyMatch(item -> item.getProduct().getOwner() != null && item.getProduct().getOwner().getId().equals(currentUser.getId()));

            if (!isCustomer && !isOwner) {
                throw new AccessDeniedException("Bạn không có quyền xem lịch sử thanh toán của đơn hàng này");
            }
        }

        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    private String buildTxnRef(Integer orderId) {
        return orderId + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);
    }
}
