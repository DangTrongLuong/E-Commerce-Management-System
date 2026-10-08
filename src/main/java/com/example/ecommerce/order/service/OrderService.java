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
import com.example.ecommerce.order.enums.OrderPaymentStatus;
import com.example.ecommerce.ticket.repository.TicketHistoryRepository;
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
    OrderMapper orderMapper;
    SecurityUtils securityUtils;
    ApplicationEventPublisher eventPublisher;

    // =========================================================================
    // 1. TẠO ĐƠN HÀNG (ORDER CREATION)
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public List<OrderResponse> createOrders(OrderCreationRequest request) {
        securityUtils.verifyUserOrAdmin(Long.valueOf(request.getCustomerId()));

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với mã ID: " + request.getCustomerId()));

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
                savedOrder.setStatus(OrderStatus.CONFIRMED);
                orderRepository.save(savedOrder);

                eventPublisher.publishEvent(new OrderInvoiceApprovedEvent(
                        savedOrder.getId(),
                        savedOrder.getCustomer() != null ? savedOrder.getCustomer().getEmail() : null));

            } else if (total.compareTo(new BigDecimal("5000000")) >= 0) {
                requiredLevel = 2;
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
                    .fromStatus(TicketStatus.PENDING_APPROVAL)
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

        if (currentStatus == OrderStatus.PROCESSING && newStatus == OrderStatus.COMPLETED) {
            AppUser currentUser = securityUtils.getCurrentUser();
            verifyProductOwnerOrAdmin(order, currentUser);

            order.setStatus(OrderStatus.COMPLETED);
            return orderMapper.toResponse(orderRepository.save(order));
        }

        throw new InvalidOrderStatusException("Không thể chuyển trạng thái đơn hàng từ " + currentStatus + " sang " + newStatus
                + " qua API này. Vui lòng sử dụng Quy trình Phê duyệt.");
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
}
