package com.example.ecommerce.service;

import com.example.ecommerce.dto.request.OrderCreationRequest;
import com.example.ecommerce.dto.request.OrderItemRequest;
import com.example.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.example.ecommerce.dto.response.OrderResponse;
import com.example.ecommerce.entity.Customer;
import com.example.ecommerce.entity.Order;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderService orderService;

    private Customer sampleCustomer;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleCustomer = Customer.builder()
                .id(1)
                .name("Nguyen Van A")
                .email("a@gmail.com")
                .phone("0987654321")
                .build();

        sampleProduct = Product.builder()
                .Id(101)
                .name("Laptop Dell XPS")
                .price(new BigDecimal("1500.00"))
                .stock(10)
                .status(ProductStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("Create Order Tests")
    class CreateOrderTests {

        @Test
        @DisplayName("createOrder thành công khi thông tin hợp lệ và đủ tồn kho")
        void createOrder_Success() {
            OrderCreationRequest request = OrderCreationRequest.builder()
                    .customerId(1)
                    .items(List.of(
                            OrderItemRequest.builder()
                                    .productId(101)
                                    .quantity(2)
                                    .build()
                    ))
                    .build();

            Order savedOrder = Order.builder()
                    .Id(1001)
                    .customer(sampleCustomer)
                    .status(OrderStatus.PENDING)
                    .totalAmount(new BigDecimal("3000.00"))
                    .build();

            OrderResponse expectedResponse = OrderResponse.builder()
                    .id(1001)
                    .status(OrderStatus.PENDING)
                    .totalAmount(new BigDecimal("3000.00"))
                    .build();

            when(customerRepository.findById(1)).thenReturn(Optional.of(sampleCustomer));
            when(productRepository.findById(101)).thenReturn(Optional.of(sampleProduct));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(orderMapper.toResponse(savedOrder)).thenReturn(expectedResponse);

            OrderResponse response = orderService.createOrder(request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1001);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);

            assertThat(sampleProduct.getStock()).isEqualTo(8);
            verify(productRepository).save(sampleProduct);

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            Order capturedOrder = orderCaptor.getValue();
            assertThat(capturedOrder.getCustomer()).isEqualTo(sampleCustomer);
            assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(capturedOrder.getOrderItems()).hasSize(1);
        }

        @Test
        @DisplayName("createOrder thất bại khi không tìm thấy khách hàng (customerNotFound)")
        void createOrder_CustomerNotFound() {
            OrderCreationRequest request = OrderCreationRequest.builder()
                    .customerId(999)
                    .items(List.of(
                            OrderItemRequest.builder()
                                    .productId(101)
                                    .quantity(1)
                                    .build()
                    ))
                    .build();

            when(customerRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy khách hàng");

            verify(orderRepository, never()).save(any());
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("createOrder thất bại khi sản phẩm không đủ tồn kho (insufficientStock)")
        void createOrder_InsufficientStock() {
            OrderCreationRequest request = OrderCreationRequest.builder()
                    .customerId(1)
                    .items(List.of(
                            OrderItemRequest.builder()
                                    .productId(101)
                                    .quantity(15)
                                    .build()
                    ))
                    .build();

            when(customerRepository.findById(1)).thenReturn(Optional.of(sampleCustomer));
            when(productRepository.findById(101)).thenReturn(Optional.of(sampleProduct));

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("không đủ tồn kho");

            assertThat(sampleProduct.getStock()).isEqualTo(10);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("createOrder thất bại khi sản phẩm không ở trạng thái ACTIVE")
        void createOrder_InactiveProduct() {
            sampleProduct.setStatus(ProductStatus.DEACTIVE);

            OrderCreationRequest request = OrderCreationRequest.builder()
                    .customerId(1)
                    .items(List.of(
                            OrderItemRequest.builder()
                                    .productId(101)
                                    .quantity(1)
                                    .build()
                    ))
                    .build();

            when(customerRepository.findById(1)).thenReturn(Optional.of(sampleCustomer));
            when(productRepository.findById(101)).thenReturn(Optional.of(sampleProduct));

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("không khả dụng để mua");
        }
    }

    @Nested
    @DisplayName("Order Status Transition Tests")
    class OrderStatusTransitionTests {

        @Test
        @DisplayName("Chuyển trạng thái hợp lệ: PENDING -> CONFIRMED")
        void updateOrderStatus_PendingToConfirmed_Success() {
            Order order = Order.builder().Id(100).status(OrderStatus.PENDING).build();
            OrderStatusUpdateRequest updateRequest = OrderStatusUpdateRequest.builder()
                    .orderStatus(OrderStatus.CONFIRMED)
                    .build();

            Order updatedOrder = Order.builder().Id(100).status(OrderStatus.CONFIRMED).build();
            OrderResponse responseDto = OrderResponse.builder().id(100).status(OrderStatus.CONFIRMED).build();

            when(orderRepository.findById(100)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(updatedOrder);
            when(orderMapper.toResponse(updatedOrder)).thenReturn(responseDto);

            OrderResponse response = orderService.updateOrderStatus(100, updateRequest);

            assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Chuyển trạng thái hợp lệ: CONFIRMED -> COMPLETED")
        void updateOrderStatus_ConfirmedToCompleted_Success() {
            Order order = Order.builder().Id(100).status(OrderStatus.CONFIRMED).build();
            OrderStatusUpdateRequest updateRequest = OrderStatusUpdateRequest.builder()
                    .orderStatus(OrderStatus.COMPLETED)
                    .build();

            Order updatedOrder = Order.builder().Id(100).status(OrderStatus.COMPLETED).build();
            OrderResponse responseDto = OrderResponse.builder().id(100).status(OrderStatus.COMPLETED).build();

            when(orderRepository.findById(100)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(updatedOrder);
            when(orderMapper.toResponse(updatedOrder)).thenReturn(responseDto);

            OrderResponse response = orderService.updateOrderStatus(100, updateRequest);

            assertThat(response.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Chuyển trạng thái bất hợp lệ: PENDING -> COMPLETED ném InvalidOrderStatusException")
        void updateOrderStatus_PendingToCompleted_ThrowsException() {
            Order order = Order.builder().Id(100).status(OrderStatus.PENDING).build();
            OrderStatusUpdateRequest updateRequest = OrderStatusUpdateRequest.builder()
                    .orderStatus(OrderStatus.COMPLETED)
                    .build();

            when(orderRepository.findById(100)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateOrderStatus(100, updateRequest))
                    .isInstanceOf(InvalidOrderStatusException.class)
                    .hasMessageContaining("Không thể chuyển trạng thái đơn hàng từ PENDING sang COMPLETED");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Chuyển trạng thái bất hợp lệ: COMPLETED -> CANCELLED ném InvalidOrderStatusException")
        void updateOrderStatus_CompletedToCancelled_ThrowsException() {
            Order order = Order.builder().Id(100).status(OrderStatus.COMPLETED).build();
            OrderStatusUpdateRequest updateRequest = OrderStatusUpdateRequest.builder()
                    .orderStatus(OrderStatus.CANCELLED)
                    .build();

            when(orderRepository.findById(100)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateOrderStatus(100, updateRequest))
                    .isInstanceOf(InvalidOrderStatusException.class)
                    .hasMessageContaining("Không thể chuyển trạng thái đơn hàng từ COMPLETED sang CANCELLED");

            verify(orderRepository, never()).save(any());
        }
    }
}
