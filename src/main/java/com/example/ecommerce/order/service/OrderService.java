package com.example.ecommerce.order.service;

import com.example.ecommerce.common.exception.BadRequestExeption;
import com.example.ecommerce.common.exception.ConflictException;
import com.example.ecommerce.common.exception.InsufficientStockException;
import com.example.ecommerce.common.exception.InvalidOrderStatusException;
import com.example.ecommerce.common.exception.OrderNotEditableException;
import com.example.ecommerce.common.exception.ProductInactiveException;
import com.example.ecommerce.common.exception.QuantityLimitExceededException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.customer.enums.CustomerStatus;
import com.example.ecommerce.customer.repository.CustomerRepository;
import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.entity.OrderItem;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.order.event.OrderInvoiceApprovedEvent;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.product.enums.ProductStatus;
import com.example.ecommerce.product.repository.ProductRepository;
import com.example.ecommerce.ticket.entity.PurchaseTicket;
import com.example.ecommerce.ticket.entity.TicketHistory;
import com.example.ecommerce.ticket.enums.TicketAction;
import com.example.ecommerce.ticket.enums.TicketStatus;
import com.example.ecommerce.ticket.repository.PurchaseTicketRepository;
import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.user.enums.Role;
import com.example.ecommerce.order.dto.OrderCreationRequest;
import com.example.ecommerce.order.dto.OrderItemRequest;
import com.example.ecommerce.order.dto.OrderStatusUpdateRequest;
import com.example.ecommerce.order.dto.UpdateOrderItemQuantityRequest;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.order.mapper.OrderMapper;
import com.example.ecommerce.common.util.SecurityUtils;
import com.example.ecommerce.common.util.SortUtils;
import com.example.ecommerce.order.dto.OrderRefundActionRequest;
import com.example.ecommerce.order.dto.OrderRefundRequest;
import com.example.ecommerce.order.dto.OrderStatusHistoryResponse;
import com.example.ecommerce.order.enums.OrderPaymentStatus;
import com.example.ecommerce.order.repository.OrderStatusHistoryRepository;
import com.example.ecommerce.ticket.repository.TicketHistoryRepository;
import com.example.ecommerce.common.exception.IdempotencyKeyRequiredException;
import com.example.ecommerce.common.exception.IdempotencyKeyReusedException;
import com.example.ecommerce.common.exception.OrderNotCheckoutableException;
import com.example.ecommerce.order.dto.OrderCheckoutResponse;
import com.example.ecommerce.order.entity.IdempotencyRecord;
import com.example.ecommerce.order.repository.IdempotencyRecordRepository;
import com.example.ecommerce.payment.dto.PaymentCreationRequest;
import com.example.ecommerce.payment.dto.PaymentUrlResponse;
import com.example.ecommerce.payment.service.VnPayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class OrderService {

    OrderRepository orderRepository;
    CustomerRepository customerRepository;
    ProductRepository productRepository;
    PurchaseTicketRepository ticketRepository;
    TicketHistoryRepository historyRepository;
    OrderStatusHistoryRepository orderStatusHistoryRepository;
    IdempotencyRecordRepository idempotencyRecordRepository;
    OrderStatusStateMachine stateMachine;
    VnPayService vnPayService;
    ObjectMapper objectMapper;
    OrderMapper orderMapper;
    SecurityUtils securityUtils;
    ApplicationEventPublisher eventPublisher;

    // =========================================================================
    // 1. TẠO ĐƠN HÀNG (ORDER CREATION)
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public List<OrderResponse> createOrders(OrderCreationRequest request) {
        final Integer targetCustomerId;
        if (request.getCustomerId() != null) {
            targetCustomerId = request.getCustomerId();
        } else {
            AppUser currentUser = securityUtils.getCurrentUser();
            if (currentUser.getCustomer() != null) {
                targetCustomerId = currentUser.getCustomer().getId();
            } else {
                throw new BadRequestExeption("Người đặt hàng (customerId) không thể để trống");
            }
        }

        securityUtils.verifyUserOrAdmin(Long.valueOf(targetCustomerId));

        Customer customer = customerRepository.findById(targetCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với mã ID: " + targetCustomerId));

        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new BadRequestExeption("Tài khoản khách hàng đang ở trạng thái ngưng hoạt động");
        }

        // Xử lý các sp trùng lặp và cộng số lượng
        Map<Integer, Integer> productQuantityMap = new HashMap<>();
        for (OrderItemRequest item : request.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestExeption("Số lượng sản phẩm #" + item.getProductId() + " phải lớn hơn 0");
            }
            productQuantityMap.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        // Nạp trước tất cả sp để xử lý
        Map<Integer, Product> productMap = productRepository.findAllById(productQuantityMap.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity())); // p -> p

        // Nhóm sản phẩm theo PO sở hữu (product.getOwner())
        Map<AppUser, Map<Product, Integer>> itemsByPoOwner = new HashMap<>();

        for (Map.Entry<Integer, Integer> entry : productQuantityMap.entrySet()) {
            Integer productId = entry.getKey();
            Integer totalQuantity = entry.getValue();

            Product product = productMap.get(productId);
            if (product == null) {
                throw new ResourceNotFoundException("Không tìm thấy sản phẩm với mã ID: " + productId);
            }

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new ConflictException("Sản phẩm '" + product.getName() + "' đang ở trạng thái ngừng kinh doanh");
            }

            AppUser owner = product.getOwner();
            itemsByPoOwner
                    .computeIfAbsent(owner, k -> new HashMap<>())
                    .put(product, totalQuantity);
        }

        List<OrderResponse> createdOrderResponses = new ArrayList<>();
        AppUser requester = securityUtils.getCurrentUser();

        for (Map.Entry<AppUser, Map<Product, Integer>> entry : itemsByPoOwner.entrySet()) {
            AppUser poOwner = entry.getKey();
            Map<Product, Integer> poProductQuantityMap = entry.getValue();

            Order order = Order.builder()
                    .customer(customer)
                    .status(OrderStatus.PENDING)
                    .build();

            for (Map.Entry<Product, Integer> itemEntry : poProductQuantityMap.entrySet()) {
                Product product = itemEntry.getKey();
                Integer quantity = itemEntry.getValue();

                if (product.getStock() < quantity) {
                    throw new InsufficientStockException(
                            "Sản phẩm '" + product.getName() + "' không đủ số lượng tồn kho (Hiện có: "
                                    + product.getStock() + ", Yêu cầu: " + quantity + ")");
                }

                OrderItem orderItem = OrderItem.builder()
                        .product(product)
                        .quantity(quantity)
                        .unitPrice(product.getPrice())
                        .build();
                orderItem.caculateSubtotal();
                order.addItems(orderItem);

                int updateStock = productRepository.decreaseStockAtomic(product.getId(), quantity);
                if (updateStock == 0) {
                    throw new InsufficientStockException(
                            "Sản phẩm '" + product.getName() + "' không đủ số lượng tồn kho (Hiện có: "
                                    + product.getStock() + ", Yêu cầu: " + quantity + ")");
                }
            }

            order.recaculateTotalAmount();
            Order savedOrder = orderRepository.save(order);

            BigDecimal total = savedOrder.getTotalAmount();
            int requiredLevel = 1;
            TicketStatus ticketStatus = TicketStatus.PENDING_APPROVAL;

            if (total.compareTo(new BigDecimal("500000")) < 0) {
                requiredLevel = 0;
                ticketStatus = TicketStatus.APPROVED;
            }

            PurchaseTicket ticket = PurchaseTicket.builder()
                    .order(savedOrder)
                    .requester(requester)
                    .approver(poOwner)
                    .status(ticketStatus)
                    .requiredLevel(requiredLevel)
                    .currentLevel(requiredLevel == 0 ? 0 : 1)
                    .dueAt(LocalDateTime.now().plusHours(48))
                    .decidedAt(requiredLevel == 0 ? LocalDateTime.now() : null)
                    .build();

            TicketHistory createHistory = TicketHistory.builder()
                    .ticket(ticket)
                    .action(TicketAction.CREATE)
                    .fromStatus(ticketStatus)
                    .toStatus(ticketStatus)
                    .actor(requester)
                    .comment("Sub-order created for PO #" + (poOwner != null ? poOwner.getId() : "N/A"))
                    .build();
            ticket.addHistory(createHistory);

            PurchaseTicket savedTicket = ticketRepository.saveAndFlush(ticket);
            log.info("Created order {} with ticket {} for PO #{}", savedOrder.getId(), savedTicket.getId(),
                    poOwner != null ? poOwner.getId() : "N/A");

            createdOrderResponses.add(orderMapper.toResponse(savedOrder));
        }

        return createdOrderResponses;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(OrderCreationRequest request) {
        List<OrderResponse> orders = createOrders(request);
        return orders.get(0);
    }

    // =========================================================================
    // REQ-02. CHECKOUT ĐƠN HÀNG VỚI IDEMPOTENCY-KEY & VNPAY
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public OrderCheckoutResponse checkoutOrder(Integer id, String idempotencyKey, String bankCode, HttpServletRequest request) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IdempotencyKeyRequiredException("Header Idempotency-Key là bắt buộc khi checkout");
        }

        // 1. Kiểm tra Idempotency Record
        Optional<IdempotencyRecord> recordOpt = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);
        if (recordOpt.isPresent()) {
            IdempotencyRecord record = recordOpt.get();
            if (!record.getOrderId().equals(id)) {
                throw new IdempotencyKeyReusedException(
                        "Khóa Idempotency-Key '" + idempotencyKey + "' đã được sử dụng cho đơn hàng khác #" + record.getOrderId());
            }

            // Cùng key, cùng orderId -> Kiểm tra hạn 24 giờ
            LocalDateTime cutoff24h = LocalDateTime.now().minusHours(24);
            if (record.getCreatedAt().isAfter(cutoff24h)) {
                try {
                    log.info("Idempotent hit cho đơn hàng #{}, key {}. Trả về kết quả checkout trước đó.", id, idempotencyKey);
                    return objectMapper.readValue(record.getResponseJson(), OrderCheckoutResponse.class);
                } catch (Exception e) {
                    log.error("Lỗi parse responseJson từ IdempotencyRecord cho key {}", idempotencyKey, e);
                }
            }
        }

        // 2. Đọc đơn hàng với khóa bi quan
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã ID: " + id));

        securityUtils.verifyUserOrAdmin(Long.valueOf(order.getCustomer().getId()));

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new OrderNotCheckoutableException(
                    "Đơn hàng #" + order.getId() + " không ở trạng thái được phép checkout (chỉ cho phép khi PENDING hoặc AWAITING_PAYMENT). Trạng thái hiện tại: " + order.getStatus());
        }

        // Kiểm tra vé phê duyệt (PurchaseTicket) nếu có
        Optional<PurchaseTicket> ticketOpt = ticketRepository.findByOrderId(Long.valueOf(order.getId()));
        if (ticketOpt.isPresent() && ticketOpt.get().getStatus() != TicketStatus.APPROVED) {
            throw new OrderNotCheckoutableException(
                    "Đơn hàng #" + order.getId() + " chưa được phê duyệt vé mua hàng (Trạng thái vé: "
                            + ticketOpt.get().getStatus() + "). Vui lòng chờ Người kiểm duyệt duyệt đơn trước khi thanh toán!");
        }

        // 3. Tạo URL thanh toán VNPay (VnPayService sẽ chuyển order sang AWAITING_PAYMENT & tạo PaymentTransaction)
        PaymentCreationRequest paymentRequest = PaymentCreationRequest.builder()
                .orderId(order.getId())
                .bankCode(bankCode)
                .build();
        PaymentUrlResponse paymentUrlResponse = vnPayService.createPaymentUrl(paymentRequest, request);

        OrderCheckoutResponse checkoutResponse = OrderCheckoutResponse.builder()
                .orderId(order.getId())
                .status(OrderStatus.AWAITING_PAYMENT)
                .paymentId(paymentUrlResponse.getPaymentId())
                .amount(order.getTotalAmount())
                .paymentUrl(paymentUrlResponse.getPaymentUrl())
                .expiresAt(paymentUrlResponse.getExpireAt())
                .build();

        // 4. Lưu Idempotency Record để bảo đảm tính idempotent trong 24 giờ
        try {
            String responseJson = objectMapper.writeValueAsString(checkoutResponse);
            IdempotencyRecord newRecord = IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .orderId(order.getId())
                    .responseJson(responseJson)
                    .createdAt(LocalDateTime.now())
                    .build();
            idempotencyRecordRepository.save(newRecord);
        } catch (Exception e) {
            log.error("Lỗi khi lưu IdempotencyRecord cho đơn hàng #{}", order.getId(), e);
        }

        return checkoutResponse;
    }

    // =========================================================================
    // 2. CHỈNH SỬA SẢN PHẨM TRONG ĐƠN HÀNG (REQ-01 ITEM MANAGEMENT)
    // =========================================================================

    /**
     * @param orderId
     * @param request
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse addItemToOrder(Integer orderId, OrderItemRequest request) {
        Order order = findOrderByIdOrThrow(orderId);

        // kiểm tra quyền truy cập
        verifyOrderVisibility(order);

        // cho phép sửa đơn hàng PENDING
        validateOrderIsPending(order);

        Integer productId = request.getProductId();
        Integer requestedQuantity = request.getQuantity();

        Product product = findProductByIdOrThrow(productId);
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("Số lượng thêm vào phải lớn hơn 0");
        }

        // Kiểm tra sp ACTIVE
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ProductInactiveException("Sản phẩm '" + product.getName() + "' đang ở trạng thái INACTIVE");
        }

        // Tìm item trong đơn hàng hiện có và gộp
        Optional<OrderItem> existingItemOpt = order.getOrderItems().stream()
                .filter(item -> Objects.equals(item.getProduct().getId(), productId))
                .findFirst();

        int currentQuantity = existingItemOpt.map(OrderItem::getQuantity).orElse(0);
        int newTotalQuantity = currentQuantity + requestedQuantity;

        // Kiểm tra giới hạn quantity(1-99)
        if (newTotalQuantity > 99) {
            throw new QuantityLimitExceededException(
                    "Tổng số lượng sản phẩm '" + product.getName() + "' vượt quá giới hạn 99 (hiện tại: "
                            + currentQuantity + ", cộng thêm: " + requestedQuantity + ")");
        }

        // trừ stock tồn kho
        int updatedStock = productRepository.decreaseStockAtomic(productId, requestedQuantity);
        if (updatedStock == 0) {
            throw new InsufficientStockException("Sản phẩm '" + product.getName() + "' không đủ số lượng tồn kho");
        }

        // cập nhật hoặc thêm mới order item
        if (existingItemOpt.isPresent()) {
            OrderItem existingItem = existingItemOpt.get();
            existingItem.setQuantity(newTotalQuantity);
        } else {
            OrderItem newItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(requestedQuantity)
                    .unitPrice(product.getPrice())
                    .build();

            newItem.caculateSubtotal();
            order.addItems(newItem);
        }

        // Tính lại tổng tiền đơn hàng
        order.recaculateTotalAmount();
        Order savedOrder = orderRepository.save(order);
        syncTicketStatusWithOrderTotal(savedOrder);
        log.info("Thêm sản phẩm #{} (số lượng {}) vào đơn hàng #{} thành công", productId, requestedQuantity, orderId);

        return orderMapper.toResponse(savedOrder);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse updateOrderItemQuantity(Integer orderId, Integer productId,
            UpdateOrderItemQuantityRequest request) {
        Order order = findOrderByIdOrThrow(orderId);

        verifyOrderVisibility(order);

        validateOrderIsPending(order);

        OrderItem existingItem = findOrderItemInOrderOrThrow(order, productId);

        int oldQuantity = existingItem.getQuantity();
        int newQuantity = request.getQuantity();
        int diff = newQuantity - oldQuantity;

        if (diff == 0)
            return orderMapper.toResponse(order);

        if (diff > 0) {
            int updatedStock = productRepository.decreaseStockAtomic(productId, diff);
            if (updatedStock == 0) {
                throw new InsufficientStockException(
                        "Sản phẩm '" + existingItem.getProduct().getName() + "' không đủ số lượng tồn kho");
            }
        } else {
            productRepository.increaseStockAtomic(productId, Math.abs(diff));
        }

        existingItem.setQuantity(newQuantity);

        order.recaculateTotalAmount();

        Order saveOrder = orderRepository.save(order);
        syncTicketStatusWithOrderTotal(saveOrder);

        log.info("Cập nhật số lượng sản phẩm #{} trong đơn hàng #{} từ {} -> {} thành công", productId, orderId,
                oldQuantity, newQuantity);
        return orderMapper.toResponse(saveOrder);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Integer orderId) {
        Order order = findOrderByIdOrThrow(orderId);
        verifyOrderVisibility(order);
        validateOrderIsPending(order);

        for (OrderItem item : order.getOrderItems()) {
            productRepository.increaseStockAtomic(item.getProduct().getId(), item.getQuantity());
        }

        ticketRepository.findByOrderId(Long.valueOf(orderId)).ifPresent(ticketRepository::delete);

        orderRepository.delete(order);

        log.info("Đã xóa đơn hàng #{}", orderId);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse deleteOrderItem(Integer orderId, Integer productId) {
        Order order = findOrderByIdOrThrow(orderId);

        verifyOrderVisibility(order);

        validateOrderIsPending(order);

        OrderItem existingItem = findOrderItemInOrderOrThrow(order, productId);

        productRepository.increaseStockAtomic(productId, existingItem.getQuantity());
        order.getOrderItems().remove(existingItem);

        order.recaculateTotalAmount();
        Order savedOrder = orderRepository.save(order);
        syncTicketStatusWithOrderTotal(savedOrder);

        log.info("Đã xóa sản phẩm #{} khỏi đơn hàng #{}, hoàn lại stock {}", productId, orderId,
                existingItem.getQuantity());
        return orderMapper.toResponse(savedOrder);
    }

    // =========================================================================
    // 3. TRUY VẤN ĐƠN HÀNG (ORDER QUERY & GETTERS)
    // =========================================================================

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(int id) {
        Order order = findOrderByIdOrThrow(id);
        verifyOrderVisibility(order);
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrders(OrderStatus status, int page, int size, String sort) {
        int cappedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(page, cappedSize, SortUtils.buildSort(sort, "Id"));

        AppUser currentUser = securityUtils.getCurrentUser();
        Specification<Order> specification = Specification.unrestricted();

        if (currentUser.getRole() == Role.USER) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("customer").get("Id"), currentUser.getCustomer().getId()));
        } else if (currentUser.getRole() == Role.PRODUCT_OWNER) {
            specification = specification.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(root.join("orderItems").join("product").join("owner").get("id"), currentUser.getId());
            });
        }

        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        Page<Order> orderPage = orderRepository.findAll(specification, pageable);
        return PageResponse.of(orderPage.map(orderMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrdersByCustomer(int customerId, OrderStatus status, int page, int size,
            String sort) {
        securityUtils.verifyUserOrAdmin(Long.valueOf(customerId));

        int cappedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(page, cappedSize, SortUtils.buildSort(sort, "Id"));

        Specification<Order> specification = (root, query, cb) -> cb.equal(root.get("customer").get("Id"), customerId);
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        Page<Order> orderPage = orderRepository.findAll(specification, pageable);
        return PageResponse.of(orderPage.map(orderMapper::toResponse));
    }

    // =========================================================================
    // 4. CẬP NHẬT TRẠNG THÁI & HOÀN TIỀN (ORDER STATUS & REFUND WORKFLOW)
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse updateOrderStatus(int id, OrderStatusUpdateRequest request) {
        Order order = findOrderByIdOrThrow(id);

        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = request.getOrderStatus();

        if (currentStatus == newStatus) {
            return orderMapper.toResponse(order);
        }

        AppUser currentUser = securityUtils.getCurrentUser();
        verifyProductOwnerOrAdmin(order, currentUser);

        if (currentStatus == OrderStatus.PAID && newStatus == OrderStatus.CONFIRMED) {
            stateMachine.transition(order, OrderStatus.CONFIRMED, currentUser.getEmail(), "Admin xác nhận đơn hàng");
            return orderMapper.toResponse(orderRepository.save(order));
        }

        if ((currentStatus == OrderStatus.CONFIRMED || currentStatus == OrderStatus.PROCESSING) && newStatus == OrderStatus.COMPLETED) {
            stateMachine.transition(order, OrderStatus.COMPLETED, currentUser.getEmail(), "Hoàn tất đơn hàng");
            return orderMapper.toResponse(orderRepository.save(order));
        }

        throw new InvalidOrderStatusException("Không thể chuyển trạng thái đơn hàng từ " + currentStatus + " sang " + newStatus
                + " qua API này.");
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse requestRefund(int id, OrderRefundRequest request) {
        Order order = findOrderByIdOrThrow(id);

        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() == Role.USER) {
            if (order.getCustomer() == null || currentUser.getCustomer() == null
                    || order.getCustomer().getId() != currentUser.getCustomer().getId()) {
                throw new AccessDeniedException("Bạn không có quyền yêu cầu hoàn tiền cho đơn hàng này");
            }
        }

        if (order.getStatus() != OrderStatus.PROCESSING
                && order.getStatus() != OrderStatus.COMPLETED) {
            throw new InvalidOrderStatusException(
                    "Chỉ đơn hàng đã thanh toán hoặc hoàn thành (PROCESSING hoặc COMPLETED) mới có thể yêu cầu hoàn tiền. Trạng thái hiện tại: "
                            + order.getStatus());
        }

        order.setStatus(OrderStatus.REFUND_REQUESTED);
        order.setCancelReason(request.getReason());
        Order savedOrder = orderRepository.save(order);

        Optional<PurchaseTicket> ticketOpt = ticketRepository.findByOrderId(Long.valueOf(id));
        if (ticketOpt.isPresent()) {
            PurchaseTicket ticket = ticketOpt.get();
            TicketHistory history = TicketHistory.builder()
                    .ticket(ticket)
                    .action(TicketAction.REFUND_REQUEST)
                    .fromStatus(ticket.getStatus())
                    .toStatus(ticket.getStatus())
                    .actor(currentUser)
                    .comment("Yêu cầu hoàn tiền: " + request.getReason())
                    .build();
            historyRepository.save(history);
        }

        log.info("Refund requested for order #{} by user #{}", id, currentUser.getId());
        return orderMapper.toResponse(savedOrder);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse approveRefund(int id, OrderRefundActionRequest request) {
        Order order = findOrderByIdOrThrow(id);

        AppUser currentUser = securityUtils.getCurrentUser();
        verifyProductOwnerOrAdmin(order, currentUser);
        validateRefundRequestedStatus(order);

        order.setStatus(OrderStatus.REFUNDED);
        order.setPaymentStatus(OrderPaymentStatus.REFUNDED);

        for (OrderItem item : order.getOrderItems()) {
            productRepository.increaseStockAtomic(item.getProduct().getId(), item.getQuantity());
        }

        Order savedOrder = orderRepository.save(order);

        Optional<PurchaseTicket> ticketOpt = ticketRepository.findByOrderId(Long.valueOf(id));
        if (ticketOpt.isPresent()) {
            PurchaseTicket ticket = ticketOpt.get();
            TicketHistory history = TicketHistory.builder()
                    .ticket(ticket)
                    .action(TicketAction.REFUND_APPROVE)
                    .fromStatus(ticket.getStatus())
                    .toStatus(ticket.getStatus())
                    .actor(currentUser)
                    .comment(request != null && request.getComment() != null ? request.getComment()
                            : "Duyệt yêu cầu hoàn tiền")
                    .build();
            historyRepository.save(history);
        }

        log.info("Refund approved for order #{} by PO/Admin #{}", id, currentUser.getId());
        return orderMapper.toResponse(savedOrder);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse rejectRefund(int id, OrderRefundActionRequest request) {
        Order order = findOrderByIdOrThrow(id);

        AppUser currentUser = securityUtils.getCurrentUser();
        verifyProductOwnerOrAdmin(order, currentUser);
        validateRefundRequestedStatus(order);

        if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
            order.setStatus(OrderStatus.PROCESSING);
        } else {
            order.setStatus(OrderStatus.COMPLETED);
        }
        Order savedOrder = orderRepository.save(order);

        Optional<PurchaseTicket> ticketOpt = ticketRepository.findByOrderId(Long.valueOf(id));
        if (ticketOpt.isPresent()) {
            PurchaseTicket ticket = ticketOpt.get();
            TicketHistory history = TicketHistory.builder()
                    .ticket(ticket)
                    .action(TicketAction.REFUND_REJECT)
                    .fromStatus(ticket.getStatus())
                    .toStatus(ticket.getStatus())
                    .actor(currentUser)
                    .comment(request != null && request.getComment() != null ? request.getComment()
                            : "Từ chối yêu cầu hoàn tiền")
                    .build();
            historyRepository.save(history);
        }

        log.info("Refund rejected for order #{} by PO/Admin #{}", id, currentUser.getId());
        return orderMapper.toResponse(savedOrder);
    }

    // =========================================================================
    // 5. HÀM BỔ TRỢ (SECURITY & PRIVATE HELPERS)
    // =========================================================================

    private Order findOrderByIdOrThrow(int id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã ID: " + id));
    }

    private Product findProductByIdOrThrow(int id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với mã ID: " + id));
    }

    private OrderItem findOrderItemInOrderOrThrow(Order order, int productId) {
        return order.getOrderItems().stream()
                .filter(item -> item.getProduct().getId() == productId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sản phẩm #" + productId + " không có trong đơn hàng #" + order.getId()));
    }

    private void validateOrderIsPending(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderNotEditableException("Đơn hàng #" + order.getId()
                    + " không thể chỉnh sửa vì đang ở trạng thái " + order.getStatus());
        }
    }

    private void validateRefundRequestedStatus(Order order) {
        if (order.getStatus() != OrderStatus.REFUND_REQUESTED) {
            throw new InvalidOrderStatusException(
                    "Đơn hàng đang không ở trạng thái yêu cầu hoàn tiền (REFUND_REQUESTED). Trạng thái hiện tại: "
                            + order.getStatus());
        }
    }

    private void syncTicketStatusWithOrderTotal(Order order) {
        if (order == null) {
            return;
        }

        Optional<PurchaseTicket> ticketOpt = ticketRepository.findByOrderId(Long.valueOf(order.getId()));
        if (ticketOpt.isEmpty()) {
            return;
        }

        PurchaseTicket ticket = ticketOpt.get();
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;

        int requiredLevel = 1;
        TicketStatus targetStatus = TicketStatus.PENDING_APPROVAL;

        if (total.compareTo(new BigDecimal("500000")) < 0) {
            requiredLevel = 0;
            targetStatus = TicketStatus.APPROVED;
        }

        TicketStatus oldStatus = ticket.getStatus();
        Integer oldLevel = ticket.getRequiredLevel();

        if (oldStatus != targetStatus || !Objects.equals(oldLevel, requiredLevel)) {
            ticket.setRequiredLevel(requiredLevel);
            ticket.setCurrentLevel(requiredLevel == 0 ? 0 : 1);
            ticket.setStatus(targetStatus);
            ticket.setDecidedAt(requiredLevel == 0 ? LocalDateTime.now() : null);

            AppUser currentUser = securityUtils.getCurrentUser();
            TicketHistory updateHistory = TicketHistory.builder()
                    .ticket(ticket)
                    .action(TicketAction.UPDATE)
                    .fromStatus(oldStatus)
                    .toStatus(targetStatus)
                    .actor(currentUser)
                    .comment("Đồng bộ trạng thái vé do thay đổi giá trị đơn hàng (" + total + " VNĐ)")
                    .build();
            ticket.addHistory(updateHistory);

            ticketRepository.save(ticket);
            log.info("Đã đồng bộ lại vé #{} cho đơn hàng #{}: {} (level {}) -> {} (level {}) do tổng tiền = {}",
                    ticket.getId(), order.getId(), oldStatus, oldLevel, targetStatus, requiredLevel, total);
        }
    }

    private void verifyProductOwnerOrAdmin(Order order, AppUser currentUser) {
        if (currentUser.getRole() != Role.ADMIN) {
            boolean isOwner = order.getOrderItems().stream()
                    .anyMatch(item -> item.getProduct().getOwner() != null
                            && item.getProduct().getOwner().getId().equals(currentUser.getId()));
            if (!isOwner) {
                throw new AccessDeniedException("Bạn không phải là chủ sở hữu sản phẩm trong đơn hàng này");
            }
        }
    }

    private void verifyOrderVisibility(Order order) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() == Role.USER && order.getCustomer() != null && currentUser.getCustomer() != null
                && order.getCustomer().getId() == currentUser.getCustomer().getId()) {
            return;
        }
        if (currentUser.getRole() == Role.PRODUCT_OWNER) {
            boolean isOwner = order.getOrderItems().stream()
                    .anyMatch(item -> item.getProduct().getOwner() != null
                            && item.getProduct().getOwner().getId().equals(currentUser.getId()));
            if (isOwner) {
                return;
            }
        }
        throw new AccessDeniedException("Bạn không có quyền truy cập hoặc thao tác trên đơn hàng #" + order.getId());
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getOrderHistory(int id) {
        Order order = findOrderByIdOrThrow(id);
        verifyOrderVisibility(order);

        return orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(id)
                .stream()
                .map(history -> OrderStatusHistoryResponse.builder()
                        .id(history.getId())
                        .orderId(history.getOrderId())
                        .fromStatus(history.getFromStatus())
                        .toStatus(history.getToStatus())
                        .changedBy(history.getChangedBy())
                        .reason(history.getReason())
                        .createdAt(history.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getOrderStatusCounts() {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser.getCustomer() == null) {
            throw new BadRequestExeption("Tài khoản chưa được liên kết với hồ sơ khách hàng.");
        }
        Integer customerId = currentUser.getCustomer().getId();

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            statusCounts.put(status.name(), 0L);
        }

        List<Object[]> rows = orderRepository.countOrdersByStatusForCustomer(customerId);

        long total = 0L;
        if (rows != null) {
            for (Object[] row : rows) {
                if (row != null && row.length >= 2 && row[0] != null) {
                    String status = row[0].toString();
                    Long count = ((Number) row[1]).longValue();
                    statusCounts.put(status, count);
                    total += count;
                }
            }
        }

        Map<String, Long> response = new LinkedHashMap<>();
        response.put("ALL", total);
        response.putAll(statusCounts);
        return response;
    }
}
