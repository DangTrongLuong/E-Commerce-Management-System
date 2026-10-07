package com.example.ecommerce.service;

import com.example.ecommerce.order.service.OrderService;

import com.example.ecommerce.order.dto.OrderCreationRequest;
import com.example.ecommerce.order.dto.OrderItemRequest;
import com.example.ecommerce.order.dto.OrderStatusUpdateRequest;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.ticket.entity.PurchaseTicket;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.product.enums.ProductStatus;
import com.example.ecommerce.user.enums.Role;
import com.example.ecommerce.user.enums.UserStatus;
import com.example.ecommerce.common.exception.ConflictException;
import com.example.ecommerce.common.exception.InsufficientStockException;
import com.example.ecommerce.common.exception.InvalidOrderStatusException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.order.mapper.OrderMapper;
import com.example.ecommerce.customer.repository.CustomerRepository;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.product.repository.ProductRepository;
import com.example.ecommerce.ticket.repository.PurchaseTicketRepository;
import com.example.ecommerce.ticket.repository.TicketHistoryRepository;
import com.example.ecommerce.common.util.SecurityUtils;
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
    private PurchaseTicketRepository ticketRepository;

    @Mock
    private TicketHistoryRepository historyRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private com.example.ecommerce.order.service.PdfInvoiceService pdfInvoiceService;

    @Mock
    private com.example.ecommerce.notification.service.EmailService emailService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    private Customer sampleCustomer;
    private Product sampleProduct;
    private AppUser sampleUser;

    @BeforeEach
    void setUp() {
        sampleCustomer = Customer.builder()
                .id(1)
                .name("Nguyen Van A")
                .email("a@gmail.com")
                .phone("0987654321")
                .build();

        sampleUser = AppUser.builder()
                .id(1L)
                .email("a@gmail.com")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .customer(sampleCustomer)
                .build();

        sampleProduct = Product.builder()
                .Id(101)
                .name("Laptop Dell XPS")
                .price(new BigDecimal("1500.00"))
                .stock(10)
                .status(ProductStatus.ACTIVE)
                .owner(sampleUser)
                .build();
    }

    @Nested
    @DisplayName("Create Order Tests")
    class CreateOrderTests {

        @Test
        @DisplayName("createOrder successfully creates order and purchase ticket")
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
            when(productRepository.findAllById(any())).thenReturn(List.of(sampleProduct));
            when(productRepository.decreaseStockAtomic(eq(101), anyInt())).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(ticketRepository.saveAndFlush(any(PurchaseTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(securityUtils.getCurrentUser()).thenReturn(sampleUser);
            when(orderMapper.toResponse(any(Order.class))).thenReturn(expectedResponse);

            OrderResponse response = orderService.createOrder(request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1001);
            verify(productRepository).decreaseStockAtomic(101, 2);
            verify(ticketRepository).saveAndFlush(any(PurchaseTicket.class));
        }

        @Test
        @DisplayName("createOrder fails when customer is not found")
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
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("createOrder fails when stock is insufficient")
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
            when(productRepository.findAllById(any())).thenReturn(List.of(sampleProduct));

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(InsufficientStockException.class);

            verify(orderRepository, never()).save(any());
        }
    }
}
