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
import com.example.ecommerce.common.exception.OrderNotCheckoutableException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.order.entity.OrderItem;
import com.example.ecommerce.order.entity.SubOrder;
import com.example.ecommerce.order.repository.SubOrderRepository;
import com.example.ecommerce.order.service.OrderStatusStateMachine;
import com.example.ecommerce.product.repository.ProductRepository;
import com.example.ecommerce.payment.mapper.PaymentMapper;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.payment.repository.PaymentTransactionRepository;
import com.example.ecommerce.ticket.entity.PurchaseTicket;
import com.example.ecommerce.ticket.enums.TicketStatus;
import com.example.ecommerce.ticket.repository.PurchaseTicketRepository;
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
    SubOrderRepository subOrderRepository;
    ProductRepository productRepository;
    PurchaseTicketRepository ticketRepository;
    OrderStatusStateMachine stateMachine;
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

        SubOrder targetSubOrder = null;
        if (paymentCreationRequest.getSubOrderId() != null) {
            targetSubOrder = subOrderRepository.findByOrderIdAndId(order.getId(), paymentCreationRequest.getSubOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy kiện hàng #" + paymentCreationRequest.getSubOrderId() + " trong đơn hàng #" + order.getId()));

            if (targetSubOrder.getPaymentStatus() == OrderPaymentStatus.PAID) {
                throw new OrderNotCheckoutableException("Kiện hàng " + targetSubOrder.getSubOrderCode() + " đã được thanh toán trước đó.");
            }

            PurchaseTicket subTicket = targetSubOrder.getTicket();
            if (subTicket == null) {
                subTicket = ticketRepository.findBySubOrderId(targetSubOrder.getId()).orElse(null);
            }
            if (subTicket != null && subTicket.getStatus() != TicketStatus.APPROVED) {
                throw new OrderNotCheckoutableException(
                        "Kiện hàng " + targetSubOrder.getSubOrderCode() + " chưa được phê duyệt vé mua hàng (Trạng thái vé: "
                                + subTicket.getStatus() + "). Vui lòng chờ Người kiểm duyệt duyệt đơn trước khi thanh toán!");
            }

            if (targetSubOrder.getStatus() == OrderStatus.PENDING) {
                AppUser currentUser = securityUtils.getCurrentUser();
                stateMachine.transitionSubOrder(targetSubOrder, OrderStatus.AWAITING_PAYMENT,
                        currentUser != null ? currentUser.getEmail() : "USER", "Initiated sub-order payment");
                subOrderRepository.save(targetSubOrder);
            }
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.AWAITING_PAYMENT);
                orderRepository.save(order);
            }
        } else {
            // Thanh toán đơn tổng
            boolean hasSubOrders = (order.getSubOrders() != null && !order.getSubOrders().isEmpty());
            if (hasSubOrders) {
                List<SubOrder> unpaidSubOrders = order.getSubOrders().stream()
                        .filter(s -> s.getStatus() == OrderStatus.PENDING || s.getStatus() == OrderStatus.AWAITING_PAYMENT)
                        .toList();

                if (unpaidSubOrders.isEmpty()) {
                    throw new OrderNotCheckoutableException(
                            "Tất cả các kiện hàng thuộc đơn tổng #" + order.getId() + " đều đã được thanh toán hoặc đã bị hủy.");
                }

                for (SubOrder sub : unpaidSubOrders) {
                    PurchaseTicket subTicket = sub.getTicket();
                    if (subTicket == null) {
                        subTicket = ticketRepository.findBySubOrderId(sub.getId()).orElse(null);
                    }
                    if (subTicket != null && subTicket.getStatus() != TicketStatus.APPROVED) {
                        throw new OrderNotCheckoutableException(
                                "Không thể thanh toán gộp đơn hàng #" + order.getId() + ": Kiện hàng " + sub.getSubOrderCode()
                                        + " chưa được phê duyệt vé mua hàng (Trạng thái vé: " + subTicket.getStatus()
                                        + "). Bạn có thể thanh toán riêng các kiện hàng đã duyệt trước hoặc chờ tất cả PO duyệt xong!");
                    }
                }

                BigDecimal amountToPay = unpaidSubOrders.stream()
                        .map(SubOrder::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                order.setTotalAmount(amountToPay);

                AppUser currentUser = securityUtils.getCurrentUser();
                String changedBy = currentUser != null ? currentUser.getEmail() : "USER";
                for (SubOrder sub : unpaidSubOrders) {
                    if (sub.getStatus() == OrderStatus.PENDING) {
                        stateMachine.transitionSubOrder(sub, OrderStatus.AWAITING_PAYMENT, changedBy, "Initiated full payment");
                        subOrderRepository.save(sub);
                    }
                }
            } else {
                Optional<PurchaseTicket> ticketOpt = ticketRepository.findByOrderId(Long.valueOf(order.getId()));
                if (ticketOpt.isPresent() && ticketOpt.get().getStatus() != TicketStatus.APPROVED) {
                    throw new OrderNotCheckoutableException(
                            "Đơn hàng #" + order.getId() + " chưa được phê duyệt vé mua hàng (Trạng thái vé: "
                                    + ticketOpt.get().getStatus() + "). Vui lòng chờ Người kiểm duyệt duyệt đơn trước khi thanh toán!");
                }
            }

            if (order.getStatus() == OrderStatus.PENDING) {
                AppUser currentUser = securityUtils.getCurrentUser();
                stateMachine.transition(order, OrderStatus.AWAITING_PAYMENT, currentUser != null ? currentUser.getEmail() : "USER", "Initiated payment checkout");
                orderRepository.save(order);
            } else if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
                throw new OrderNotCheckoutableException(
                        "Đơn hàng #" + order.getId() + " không ở trạng thái được phép thanh toán. Trạng thái hiện tại: " + order.getStatus());
            }
        }

        BigDecimal payAmount = targetSubOrder != null ? targetSubOrder.getTotalAmount() : order.getTotalAmount();
        String txnRef = buildTxnRef(order.getId());
        String createDate = VnPayUtils.now();
        String expireDate = VnPayUtils.plusMinutes((vnPayProperties.getExpireMinutes()));
        String orderInfo = targetSubOrder != null
                ? "Thanh toan kien hang " + targetSubOrder.getSubOrderCode()
                : "Thanh toan don hang #" + order.getId();

        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .orderId(order.getId())
                .subOrderId(targetSubOrder != null ? targetSubOrder.getId() : null)
                .vnpTxnRef(txnRef)
                .amount(payAmount)
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
        params.put("vnp_Amount", payAmount
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
                .paymentId(paymentTransaction.getId())
                .paymentUrl(paymentUrl)
                .txnRef(txnRef)
                .amount(payAmount)
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
        paymentTransaction.setIpnConfirmed(true);

        Order order = orderRepository.findByIdForUpdate(paymentTransaction.getOrderId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã ID: " + paymentTransaction.getOrderId()));

        applyPaymentOutcome(paymentTransaction, order, isSuccess, txnRef, "IPN");

        log.info("VNPay IPN processed. txnRef={}, orderId={}, subOrderId={}, status={}",
                txnRef, paymentTransaction.getOrderId(), paymentTransaction.getSubOrderId(), paymentTransaction.getStatus());

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
            paymentTransaction.setIpnConfirmed(true);

            Order order = orderRepository.findById(paymentTransaction.getOrderId()).orElse(null);
            if (order != null) {
                applyPaymentOutcome(paymentTransaction, order, isSuccess, txnRef, "Return URL");
            } else {
                paymentTransaction.setStatus(isSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
                paymentRepository.save(paymentTransaction);
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

    private void applyPaymentOutcome(PaymentTransaction paymentTransaction, Order order, boolean isSuccess, String txnRef, String context) {
        if (isSuccess) {
            paymentTransaction.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(paymentTransaction);

            if (paymentTransaction.getSubOrderId() != null) {
                Optional<SubOrder> subOpt = subOrderRepository.findById(paymentTransaction.getSubOrderId());
                if (subOpt.isPresent()) {
                    SubOrder sub = subOpt.get();
                    stateMachine.transitionSubOrder(sub, OrderStatus.PAID, "VNPAY", context + " Payment Successful");
                    subOrderRepository.save(sub);
                    log.info("SubOrder #{} paid successfully via VNPay (txnRef={})", sub.getSubOrderCode(), txnRef);
                }
            } else {
                if (order.getStatus() == OrderStatus.CANCELLED) {
                    paymentTransaction.setStatus(PaymentStatus.REFUND_REQUIRED);
                    paymentRepository.save(paymentTransaction);
                    log.warn("Đơn hàng #{} đã bị hủy (CANCELLED) nhưng nhận được thanh toán thành công từ VNPay (txnRef={}). Chuyển giao dịch sang REFUND_REQUIRED.",
                            order.getId(), txnRef);
                } else if (order.getStatus() == OrderStatus.PAID) {
                    log.info("Đơn hàng #{} đã ở trạng thái PAID. Giao dịch VNPay lặp lại hợp lệ (txnRef={}).", order.getId(), txnRef);
                } else {
                    order.setPaymentStatus(OrderPaymentStatus.PAID);
                    stateMachine.transition(order, OrderStatus.PAID, "VNPAY", context + " Payment Successful");
                    orderRepository.save(order);
                }
            }
        } else {
            paymentTransaction.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(paymentTransaction);

            if (paymentTransaction.getSubOrderId() != null) {
                Optional<SubOrder> subOpt = subOrderRepository.findById(paymentTransaction.getSubOrderId());
                if (subOpt.isPresent()) {
                    SubOrder sub = subOpt.get();
                    sub.setPaymentStatus(OrderPaymentStatus.FAILED);
                    stateMachine.transitionSubOrder(sub, OrderStatus.CANCELLED, "VNPAY", context + " Payment Failed");
                    for (OrderItem item : sub.getOrderItems()) {
                        if (item.getProduct() != null) {
                            productRepository.increaseStockAtomic(item.getProduct().getId(), item.getQuantity());
                        }
                    }
                    subOrderRepository.save(sub);
                }
            } else {
                order.setPaymentStatus(OrderPaymentStatus.FAILED);
                if (order.getStatus() == OrderStatus.AWAITING_PAYMENT || order.getStatus() == OrderStatus.PENDING) {
                    stateMachine.transition(order, OrderStatus.CANCELLED, "VNPAY", context + " Payment Failed");
                    for (OrderItem item : order.getOrderItems()) {
                        if (item.getProduct() != null) {
                            productRepository.increaseStockAtomic(item.getProduct().getId(), item.getQuantity());
                        }
                    }
                    orderRepository.save(order);
                }
            }
        }
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
