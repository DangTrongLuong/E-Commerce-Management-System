package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.OrderCreationRequest;
import com.example.ecommerce.dto.request.OrderItemRequest;
import com.example.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.example.ecommerce.dto.response.OrderResponse;
import com.example.ecommerce.dto.response.PageResponse;
import com.example.ecommerce.entity.*;
import com.example.ecommerce.enums.*;
import com.example.ecommerce.exception.*;
import com.example.ecommerce.mapper.OrderMapper;
import com.example.ecommerce.repository.*;
import com.example.ecommerce.util.SecurityUtils;
import com.example.ecommerce.util.SortUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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
    PdfInvoiceService pdfInvoiceService;

    @Transactional
    public OrderResponse createOrder(OrderCreationRequest request) {
        // Enforce USER creating for own customer ID
        securityUtils.verifyUserOrAdmin(Long.valueOf(request.getCustomerId()));

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.getCustomerId()));

        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new BadRequestExeption("Customer account is inactive");
        }

        // Validate item duplicate productId
        Set<Integer> uniqueProductIds = new HashSet<>();
        for (OrderItemRequest item : request.getItems()) {
            if (!uniqueProductIds.add(item.getProductId())) {
                throw new BadRequestExeption("Duplicate productId in order items: " + item.getProductId());
            }
        }

        Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.PENDING)
                .build();

        AppUser productOwner = null;

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemRequest.getProductId()));

            // BR-02: Single PO check
            if (productOwner == null) {
                productOwner = product.getOwner();
            } else if (product.getOwner() != null && !product.getOwner().getId().equals(productOwner.getId())) {
                throw new MixedOwnerOrderException("Order contains products from multiple product owners");
            }

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new ConflictException("Product '" + product.getName() + "' is inactive");
            }

            if (product.getStock() < itemRequest.getQuantity()) {
                throw new InsufficientStockException(
                        "Product '" + product.getName() + "' has insufficient stock. Stock: "
                                + product.getStock() + ", requested: " + itemRequest.getQuantity());
            }

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();
            orderItem.caculateSubtotal();

            order.addItems(orderItem);

            // Deduct stock immediately
            product.setStock(product.getStock() - itemRequest.getQuantity());
            productRepository.save(product);
        }

        order.recaculateTotalAmount();
        Order savedOrder = orderRepository.save(order);

        // Determine multi-level approval requirement
        BigDecimal total = savedOrder.getTotalAmount();
        int requiredLevel = 1;
        TicketStatus ticketStatus = TicketStatus.PENDING_APPROVAL;

        if (total.compareTo(new BigDecimal("500000")) < 0) {
            requiredLevel = 0; // Auto-approve
            ticketStatus = TicketStatus.APPROVED;
            savedOrder.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(savedOrder);
            try {
                byte[] pdfBytes = pdfInvoiceService.generateInvoicePdf(savedOrder.getId());
                pdfInvoiceService.saveInvoiceToDisk(savedOrder.getId(), pdfBytes);
            } catch (Exception e) {
                log.warn("Auto-generating PDF invoice failed for auto-approved order #{}: {}", savedOrder.getId(), e.getMessage());
            }
        } else if (total.compareTo(new BigDecimal("5000000")) >= 0) {
            requiredLevel = 2; // PO + ADMIN
        }

        AppUser requester = securityUtils.getCurrentUser();

        PurchaseTicket ticket = PurchaseTicket.builder()
                .order(savedOrder)
                .requester(requester)
                .approver(productOwner)
                .status(ticketStatus)
                .requiredLevel(requiredLevel)
                .currentLevel(requiredLevel == 0 ? 0 : 1)
                .dueAt(LocalDateTime.now().plusHours(48))
                .decidedAt(requiredLevel == 0 ? LocalDateTime.now() : null)
                .build();

        PurchaseTicket savedTicket = ticketRepository.save(ticket);

        TicketHistory createHistory = TicketHistory.builder()
                .ticket(savedTicket)
                .action(TicketAction.CREATE)
                .fromStatus(TicketStatus.PENDING_APPROVAL)
                .toStatus(ticketStatus)
                .actor(requester)
                .comment("Order and ticket created")
                .build();
        historyRepository.save(createHistory);

        log.info("Created order {} with ticket {}", savedOrder.getId(), savedTicket.getId());
        return orderMapper.toResponse(savedOrder);
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
            specification = specification.and((root, query, cb) -> cb.equal(root.get("customer").get("Id"), currentUser.getCustomer().getId()));
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
    public PageResponse<OrderResponse> getOrdersByCustomer(int customerId, OrderStatus status, int page, int size, String sort) {
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

    @Transactional
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
                        .anyMatch(item -> item.getProduct().getOwner() != null && item.getProduct().getOwner().getId().equals(currentUser.getId()));
                if (!isOwner) {
                    throw new AccessDeniedException("Forbidden: Caller is not the product owner of this order");
                }
            }

            order.setStatus(OrderStatus.COMPLETED);
            return orderMapper.toResponse(orderRepository.save(order));
        }

        throw new InvalidOrderStatusException("Order status transition from " + currentStatus + " to " + newStatus + " is not allowed via PATCH API. Use Ticket API for approval workflow.");
    }

    private void verifyOrderVisibility(Order order) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() == Role.USER && order.getCustomer() != null && currentUser.getCustomer() != null && order.getCustomer().getId() == currentUser.getCustomer().getId()) {
            return;
        }
        if (currentUser.getRole() == Role.PRODUCT_OWNER) {
            boolean isOwner = order.getOrderItems().stream()
                    .anyMatch(item -> item.getProduct().getOwner() != null && item.getProduct().getOwner().getId().equals(currentUser.getId()));
            if (isOwner) {
                return;
            }
        }
        // DD-07: Return 404 for unseen resources
        throw new ResourceNotFoundException("Order not found with id: " + order.getId());
    }
}