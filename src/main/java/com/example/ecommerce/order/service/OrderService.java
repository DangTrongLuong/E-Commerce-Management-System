package com.example.ecommerce.order.service;

import com.example.ecommerce.common.exception.BadRequestExeption;
import com.example.ecommerce.common.exception.ConflictException;
import com.example.ecommerce.common.exception.InsufficientStockException;
import com.example.ecommerce.common.exception.InvalidOrderStatusException;
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

import org.apache.coyote.BadRequestException;
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

    @Transactional(rollbackFor = Exception.class)
    public List<OrderResponse> createOrders(OrderCreationRequest request) {
        securityUtils.verifyUserOrAdmin(Long.valueOf(request.getCustomerId()));

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.getCustomerId()));

        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new BadRequestExeption("Customer account is inactive");
        }

        // Xử lý các sp trùng lặp và cộng số lượng
        Map<Integer, Integer> productQuantityMap = new HashMap<>();
        for (OrderItemRequest item : request.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestExeption("Quantity must be greater than 0 for productId: " + item.getProductId());
            }
            productQuantityMap.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        Set<Integer> uniqueProductIds = productQuantityMap.keySet();

        // Nạp trước tất cả sp để xử lý
        Map<Integer, Product> productMap = productRepository.findAllById(uniqueProductIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // Nhóm sản phẩm theo PO sở hữu (product.getOwner())
        Map<AppUser, Map<Product, Integer>> itemsByPoOwner = new HashMap<>();

        for (Map.Entry<Integer, Integer> entry : productQuantityMap.entrySet()) {
            Integer productId = entry.getKey();
            Integer totalQuantity = entry.getValue();

            Product product = productMap.get(productId);
            if (product == null) {
                throw new ResourceNotFoundException("Product not found with id: " + productId);
            }

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new ConflictException("Product '" + product.getName() + "' is inactive");
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
                            "Product '" + product.getName() + "' has insufficient stock. Stock: "
                                    + product.getStock() + ", requested: " + quantity);
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
                            "Product '" + product.getName() + "' has insufficient stock. Stock: "
                                    + product.getStock() + ", requested: " + quantity);
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

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(int id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

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

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse updateOrderStatus(int id, OrderStatusUpdateRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = request.getOrderStatus();

        if (currentStatus == newStatus) {
            return orderMapper.toResponse(order);
        }

        // Section 6.3: Only allow PROCESSING -> COMPLETED via PATCH API
        if (currentStatus == OrderStatus.PROCESSING && newStatus == OrderStatus.COMPLETED) {
            AppUser currentUser = securityUtils.getCurrentUser();
            if (currentUser.getRole() != Role.ADMIN) {
                boolean isOwner = order.getOrderItems().stream()
                        .anyMatch(item -> item.getProduct().getOwner() != null
                                && item.getProduct().getOwner().getId().equals(currentUser.getId()));
                if (!isOwner) {
                    throw new AccessDeniedException("Forbidden: Caller is not the product owner of this order");
                }
            }

            order.setStatus(OrderStatus.COMPLETED);
            return orderMapper.toResponse(orderRepository.save(order));
        }

        throw new InvalidOrderStatusException("Order status transition from " + currentStatus + " to " + newStatus
                + " is not allowed via PATCH API. Use Ticket API for approval workflow.");
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
        // DD-07: Return 404 for unseen resources
        throw new ResourceNotFoundException("Order not found with id: " + order.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderResponse requestRefund(int id, OrderRefundRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() == Role.USER) {
            if (order.getCustomer() == null || currentUser.getCustomer() == null
                    || order.getCustomer().getId() != currentUser.getCustomer().getId()) {
                throw new AccessDeniedException("Forbidden: You do not own this order");
            }
        }

        if (order.getStatus() != OrderStatus.PROCESSING
                && order.getStatus() != OrderStatus.COMPLETED) {
            throw new InvalidOrderStatusException(
                    "Only paid or completed orders (PROCESSING or COMPLETED) can be refunded. Current status: "
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
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            boolean isOwner = order.getOrderItems().stream()
                    .anyMatch(item -> item.getProduct().getOwner() != null
                            && item.getProduct().getOwner().getId().equals(currentUser.getId()));
            if (!isOwner) {
                throw new AccessDeniedException("Forbidden: Caller is not the product owner of this order");
            }
        }

        if (order.getStatus() != OrderStatus.REFUND_REQUESTED) {
            throw new InvalidOrderStatusException(
                    "Order is not in REFUND_REQUESTED state. Current: " + order.getStatus());
        }

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
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            boolean isOwner = order.getOrderItems().stream()
                    .anyMatch(item -> item.getProduct().getOwner() != null
                            && item.getProduct().getOwner().getId().equals(currentUser.getId()));
            if (!isOwner) {
                throw new AccessDeniedException("Forbidden: Caller is not the product owner of this order");
            }
        }

        if (order.getStatus() != OrderStatus.REFUND_REQUESTED) {
            throw new InvalidOrderStatusException(
                    "Order is not in REFUND_REQUESTED state. Current: " + order.getStatus());
        }

        // Restore to PROCESSING if paid, otherwise COMPLETED
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
}
