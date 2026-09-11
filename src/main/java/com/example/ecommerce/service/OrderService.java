package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.OrderCreationRequest;
import com.example.ecommerce.dto.request.OrderItemRequest;
import com.example.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.example.ecommerce.dto.response.OrderResponse;
import com.example.ecommerce.dto.response.PageResponse;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.Order;
import com.example.ecommerce.entity.OrderItem;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.ProductStatus;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.exception.InvalidOrderStatusException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.mapper.OrderMapper;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.util.SortUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class OrderService {
    OrderRepository orderRepository;
    CustomerRepository customerRepository;
    ProductRepository productRepository;

    OrderMapper orderMapper;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    @Transactional
    public OrderResponse createOrder(OrderCreationRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng"));

        Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.PENDING)
                .build();

        Map<Integer, Integer> mergedQuantityByProductId = new LinkedHashMap<>();
        for (OrderItemRequest itemRequest : request.getItems()) {
            mergedQuantityByProductId.merge(
                    itemRequest.getProductId(), itemRequest.getQuantity(), Integer::sum);
        }

        for (Map.Entry<Integer, Integer> entry : mergedQuantityByProductId.entrySet()) {
            Integer productId = entry.getKey();
            Integer quantity = entry.getValue();

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy sản phẩm với id: " + productId));

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new ConflictException(
                        "Sản phẩm '" + product.getName() + "' hiện không khả dụng để mua");
            }

            if (product.getStock() < quantity) {
                throw new ConflictException(
                        "Sản phẩm '" + product.getName() + "' không đủ tồn kho. Còn lại: "
                                + product.getStock() + ", yêu cầu: " + quantity);
            }

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(quantity)
                    .unitPrice(product.getPrice())
                    .build();
            orderItem.caculateSubtotal();

            order.addItems(orderItem);

            product.setStock(product.getStock() - quantity);
            productRepository.save(product);
        }

        order.recaculateTotalAmount();

        Order savedOrder = orderRepository.save(order);
        log.info("Tạo đơn hàng thành công: {}", savedOrder.getId());

        return orderMapper.toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(int id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));
        return orderMapper.toResponse(order);
    }


    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrders(OrderStatus status, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, SortUtils.buildSort(sort,"Id"));

        Specification<Order> specification = Specification.unrestricted();
        if (status != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("status"), status));
        }

        Page<Order> orderPage = orderRepository.findAll(specification, pageable);
        Page<OrderResponse> responsePage = orderPage.map(orderMapper::toResponse);

        return PageResponse.of(responsePage);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrdersByCustomer(int customerId, OrderStatus status, int page, int size, String sort) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng"));

        Pageable pageable = PageRequest.of(page, size, SortUtils.buildSort(sort, "Id"));

        Specification<Order> specification = (root, query, cb) -> cb.equal(root.get("customer").get("Id"), customerId);
        if (status != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("status"), status));
        }

        Page<Order> orderPage = orderRepository.findAll(specification, pageable);
        Page<OrderResponse> responsePage = orderPage.map(orderMapper::toResponse);

        return PageResponse.of(responsePage);
    }

    @Transactional
    public OrderResponse updateOrderStatus(int id, OrderStatusUpdateRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));

        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = request.getOrderStatus();

        if (currentStatus == newStatus) {
            return orderMapper.toResponse(order);
        }

        Set<OrderStatus> allowedNextStatuses = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(OrderStatus.class));

        if (!allowedNextStatuses.contains(newStatus)) {
            throw new InvalidOrderStatusException(
                    "Không thể chuyển trạng thái đơn hàng từ " + currentStatus + " sang " + newStatus);
        }

        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);
        log.info("Cập nhật trạng thái đơn hàng {}: {} -> {}", id, currentStatus, newStatus);

        return orderMapper.toResponse(savedOrder);
    }



}
