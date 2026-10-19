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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.ecommerce.order.entity.SubOrder;
import com.example.ecommerce.order.repository.SubOrderRepository;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.order.dto.OrderTotalAmountResponse;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SubOrderRepository subOrderRepository;

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
            when(subOrderRepository.save(any(SubOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

    @Nested
    @DisplayName("Tests for getTotalOrderAmountForAdmin & getTotalOrderAmountForCurrentPo (Today Revenue)")
    class TotalOrderAmountTests {

        @Test
        @DisplayName("ADMIN: Tính doanh thu hôm nay của toàn hệ thống thành công (không truyền status/date)")
        void admin_getTotalOrderAmount_Success_AllStatus() {
            AppUser admin = AppUser.builder().id(99L).email("admin@shop.com").role(Role.ADMIN).build();
            when(securityUtils.getCurrentUser()).thenReturn(admin);
            when(orderRepository.sumRevenueByStatusesAndDate(anyList(), any(), any())).thenReturn(new BigDecimal("5000000.00"));
            when(orderRepository.countRevenueByStatusesAndDate(anyList(), any(), any())).thenReturn(10L);

            OrderTotalAmountResponse res = orderService.getTotalOrderAmountForAdmin(null);

            assertThat(res).isNotNull();
            assertThat(res.getTotalAmount()).isEqualByComparingTo("5000000.00");
            assertThat(res.getOrderCount()).isEqualTo(10L);
            assertThat(res.getDate()).isEqualTo(LocalDate.now());
            assertThat(res.getScope()).isEqualTo("ALL_SYSTEM");
            assertThat(res.getStatus()).isNull();
            assertThat(res.getCurrency()).isEqualTo("VND");
        }

        @Test
        @DisplayName("ADMIN: Tính doanh thu hôm nay có lọc status thành công")
        void admin_getTotalOrderAmount_Success_WithStatus() {
            AppUser admin = AppUser.builder().id(99L).email("admin@shop.com").role(Role.ADMIN).build();
            when(securityUtils.getCurrentUser()).thenReturn(admin);
            when(orderRepository.sumRevenueByStatusesAndDate(eq(List.of(OrderStatus.PAID)), any(), any()))
                    .thenReturn(new BigDecimal("3000000.00"));
            when(orderRepository.countRevenueByStatusesAndDate(eq(List.of(OrderStatus.PAID)), any(), any()))
                    .thenReturn(6L);

            OrderTotalAmountResponse res = orderService.getTotalOrderAmountForAdmin(OrderStatus.PAID);

            assertThat(res).isNotNull();
            assertThat(res.getTotalAmount()).isEqualByComparingTo("3000000.00");
            assertThat(res.getOrderCount()).isEqualTo(6L);
            assertThat(res.getDate()).isEqualTo(LocalDate.now());
            assertThat(res.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("ADMIN: Người dùng không phải ADMIN gọi API sẽ bị từ chối với message rõ ràng")
        void admin_getTotalOrderAmount_ForbiddenForNonAdmin() {
            AppUser user = AppUser.builder().id(10L).email("user@shop.com").role(Role.USER).build();
            when(securityUtils.getCurrentUser()).thenReturn(user);

            assertThatThrownBy(() -> orderService.getTotalOrderAmountForAdmin(null))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Chỉ Quản trị viên (Admin) mới có quyền xem tổng tiền của toàn bộ hệ thống");
        }

        @Test
        @DisplayName("PRODUCT_OWNER: Tính doanh thu hôm nay của chính PO thành công từ sub_orders")
        void po_getTotalOrderAmount_Success_FromSubOrders() {
            AppUser po = AppUser.builder().id(5L).email("po5@shop.com").role(Role.PRODUCT_OWNER).build();
            when(securityUtils.getCurrentUser()).thenReturn(po);
            when(subOrderRepository.countRevenueByPoOwnerIdAndDate(eq(5L), anyList(), any(), any())).thenReturn(4L);
            when(subOrderRepository.sumRevenueByPoOwnerIdAndDate(eq(5L), anyList(), any(), any())).thenReturn(new BigDecimal("1200000.00"));

            OrderTotalAmountResponse res = orderService.getTotalOrderAmountForCurrentPo(null);

            assertThat(res).isNotNull();
            assertThat(res.getTotalAmount()).isEqualByComparingTo("1200000.00");
            assertThat(res.getOrderCount()).isEqualTo(4L);
            assertThat(res.getDate()).isEqualTo(LocalDate.now());
            assertThat(res.getScope()).isEqualTo("PRODUCT_OWNER");
            assertThat(res.getPoId()).isEqualTo(5L);
            assertThat(res.getPoEmail()).isEqualTo("po5@shop.com");
        }

        @Test
        @DisplayName("PRODUCT_OWNER: Tính doanh thu hôm nay của chính PO có lọc status từ sub_orders")
        void po_getTotalOrderAmount_Success_WithStatus() {
            AppUser po = AppUser.builder().id(5L).email("po5@shop.com").role(Role.PRODUCT_OWNER).build();
            when(securityUtils.getCurrentUser()).thenReturn(po);
            when(subOrderRepository.countRevenueByPoOwnerIdAndDate(eq(5L), eq(List.of(OrderStatus.COMPLETED)), any(), any())).thenReturn(2L);
            when(subOrderRepository.sumRevenueByPoOwnerIdAndDate(eq(5L), eq(List.of(OrderStatus.COMPLETED)), any(), any()))
                    .thenReturn(new BigDecimal("800000.00"));

            OrderTotalAmountResponse res = orderService.getTotalOrderAmountForCurrentPo(OrderStatus.COMPLETED);

            assertThat(res).isNotNull();
            assertThat(res.getTotalAmount()).isEqualByComparingTo("800000.00");
            assertThat(res.getOrderCount()).isEqualTo(2L);
            assertThat(res.getDate()).isEqualTo(LocalDate.now());
            assertThat(res.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("PRODUCT_OWNER: Fallback sang order_items nếu chưa có dữ liệu sub_orders")
        void po_getTotalOrderAmount_FallbackToOrderItems() {
            AppUser po = AppUser.builder().id(5L).email("po5@shop.com").role(Role.PRODUCT_OWNER).build();
            when(securityUtils.getCurrentUser()).thenReturn(po);
            when(subOrderRepository.countRevenueByPoOwnerIdAndDate(eq(5L), anyList(), any(), any())).thenReturn(0L);
            when(orderRepository.countRevenueOrdersByProductOwnerIdAndDate(eq(5L), anyList(), any(), any())).thenReturn(3L);
            when(orderRepository.sumRevenueByProductOwnerIdAndDate(eq(5L), anyList(), any(), any())).thenReturn(new BigDecimal("950000.00"));

            OrderTotalAmountResponse res = orderService.getTotalOrderAmountForCurrentPo(null);

            assertThat(res).isNotNull();
            assertThat(res.getTotalAmount()).isEqualByComparingTo("950000.00");
            assertThat(res.getOrderCount()).isEqualTo(3L);
            assertThat(res.getDate()).isEqualTo(LocalDate.now());
            assertThat(res.getScope()).isEqualTo("PRODUCT_OWNER");
        }

        @Test
        @DisplayName("PRODUCT_OWNER: Người dùng không phải PO (USER hoặc ADMIN) gọi API bị từ chối với message rõ ràng")
        void po_getTotalOrderAmount_ForbiddenForNonPo() {
            AppUser admin = AppUser.builder().id(99L).email("admin@shop.com").role(Role.ADMIN).build();
            when(securityUtils.getCurrentUser()).thenReturn(admin);

            assertThatThrownBy(() -> orderService.getTotalOrderAmountForCurrentPo(null))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Chỉ Product Owner (PO) mới có quyền xem tổng tiền các đơn hàng của mình");
        }
    }
}

