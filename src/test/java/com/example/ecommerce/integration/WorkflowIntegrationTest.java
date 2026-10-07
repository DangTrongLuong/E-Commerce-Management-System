package com.example.ecommerce.integration;

import com.example.ecommerce.auth.dto.LoginRequest;
import com.example.ecommerce.auth.dto.RegisterRequest;
import com.example.ecommerce.auth.service.AuthService;
import com.example.ecommerce.common.exception.AccountLockedException;
import com.example.ecommerce.common.exception.BadRequestExeption;
import com.example.ecommerce.common.exception.DuplicateResourceException;
import com.example.ecommerce.common.exception.SelfApprovalNotAllowedException;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.customer.enums.CustomerStatus;
import com.example.ecommerce.customer.repository.CustomerRepository;
import com.example.ecommerce.notification.repository.NotificationRepository;
import com.example.ecommerce.order.dto.OrderCreationRequest;
import com.example.ecommerce.order.dto.OrderItemRequest;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.order.service.OrderService;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.product.enums.ProductStatus;
import com.example.ecommerce.product.repository.ProductRepository;
import com.example.ecommerce.ticket.dto.TicketActionRequest;
import com.example.ecommerce.ticket.dto.TicketResponse;
import com.example.ecommerce.ticket.entity.PurchaseTicket;
import com.example.ecommerce.ticket.enums.TicketStatus;
import com.example.ecommerce.ticket.repository.PurchaseTicketRepository;
import com.example.ecommerce.ticket.repository.TicketHistoryRepository;
import com.example.ecommerce.ticket.service.TicketService;
import com.example.ecommerce.ticket.service.TicketSlaExecutor;
import com.example.ecommerce.user.dto.UserResponse;
import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.user.enums.Role;
import com.example.ecommerce.user.enums.UserStatus;
import com.example.ecommerce.user.repository.AppUserRepository;
import com.example.ecommerce.user.service.AdminUserService;

import com.example.ecommerce.ticket.service.TicketSlaScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests covering the full Approval Workflow as defined in the spec.
 *
 * NOTE: No class-level @Transactional — each test runs in its own committed transactions
 * so that REQUIRES_NEW inner transactions (e.g. TicketSlaExecutor) can see committed data.
 * Cleanup is handled explicitly in @AfterEach.
 */
@SpringBootTest
class WorkflowIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private AdminUserService adminUserService;
    @Autowired private OrderService orderService;
    @Autowired private TicketService ticketService;
    @Autowired private TicketSlaScheduler ticketSlaScheduler;
    @org.springframework.boot.test.mock.mockito.MockBean private com.example.ecommerce.notification.service.EmailService emailService;

    // Repositories for setup & teardown
    @Autowired private AppUserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PurchaseTicketRepository ticketRepository;
    @Autowired private TicketHistoryRepository historyRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private OrderRepository orderRepository;

    private AppUser adminUser;
    private AppUser poUser;
    private AppUser customerUser;
    private Customer customer;
    private Product product1;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        // 1. Setup Admin
        adminUser = userRepository.save(AppUser.builder()
                .email("admin_test@example.com")
                .passwordHash("password_hash")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        // 2. Setup Product Owner
        poUser = userRepository.save(AppUser.builder()
                .email("po_test@example.com")
                .passwordHash("password_hash")
                .role(Role.PRODUCT_OWNER)
                .status(UserStatus.ACTIVE)
                .build());

        // 3. Setup Customer & linked User
        Customer unpersistedCustomer = Customer.builder()
                .name("John Doe")
                .email("user_test@example.com")
                .phone("0901234567")
                .status(CustomerStatus.ACTIVE)
                .build();

        customerUser = userRepository.save(AppUser.builder()
                .email("user_test@example.com")
                .passwordHash("password_hash")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .customer(unpersistedCustomer)
                .build());
        customer = customerUser.getCustomer();

        // 4. Setup Product
        product1 = productRepository.save(Product.builder()
                .name("Test Laptop")
                .price(new BigDecimal("1000000.00"))
                .stock(10)
                .status(ProductStatus.ACTIVE)
                .owner(poUser)
                .build());
    }

    /**
     * Cleans up all test data in FK-safe order after each test.
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        notificationRepository.deleteAll();
        historyRepository.deleteAll();
        ticketRepository.deleteAll();
        // Order items are cascade-deleted with orders via orphanRemoval
        orderRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();
    }

    private void authenticateAs(AppUser user) {
        String roleName = "ROLE_" + user.getRole().name();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority(roleName))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // -------------------------------------------------------------------------
    // AC-01 & AC-02: Registration
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-01 & AC-02: Register user and duplicate email check")
    void testRegistrationAndDuplicate() {
        RegisterRequest req = RegisterRequest.builder()
                .name("Jane Doe")
                .email("jane_unique@example.com")
                .phone("0912345678")
                .password("Passw0rd123")
                .build();

        UserResponse res = authService.register(req);
        assertThat(res).isNotNull();
        assertThat(res.getEmail()).isEqualTo("jane_unique@example.com");
        assertThat(res.getRole()).isEqualTo(Role.USER);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(DuplicateResourceException.class);
    }

    // -------------------------------------------------------------------------
    // AC-04: Account Lock
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-04: Login failed attempts lock account after 5 tries")
    void testAccountLockAfterFailedLogins() {
        LoginRequest wrongReq = LoginRequest.builder()
                .email("user_test@example.com")
                .password("wrongpassword")
                .build();

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login(wrongReq))
                    .isInstanceOf(BadRequestExeption.class);
        }

        // 5th attempt locks account
        assertThatThrownBy(() -> authService.login(wrongReq))
                .isInstanceOf(BadRequestExeption.class);

        // Subsequent attempt throws AccountLockedException
        assertThatThrownBy(() -> authService.login(wrongReq))
                .isInstanceOf(AccountLockedException.class);
    }

    // -------------------------------------------------------------------------
    // AC-11, AC-12: Order Creation + Approval Flow
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-11, AC-12: Order Creation and Ticket Approval Flow")
    void testOrderCreationAndApprovalFlow() {
        authenticateAs(customerUser);

        OrderCreationRequest orderReq = OrderCreationRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product1.getId()).quantity(2).build()))
                .build();

        OrderResponse orderRes = orderService.createOrder(orderReq);
        assertThat(orderRes.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(orderRes.getTotalAmount()).isEqualByComparingTo("2000000.00");

        // Verify stock deducted
        Product updatedProduct = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(8);

        // Verify Ticket created
        PurchaseTicket ticket = ticketRepository.findByOrderId(Long.valueOf(orderRes.getId())).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING_APPROVAL);

        // Separation of duties: requester cannot approve own ticket
        assertThatThrownBy(() -> ticketService.approveTicket(ticket.getId(), null))
                .isInstanceOf(SelfApprovalNotAllowedException.class);

        // PO approves ticket
        authenticateAs(poUser);
        TicketResponse approvedTicket = ticketService.approveTicket(ticket.getId(), null);
        assertThat(approvedTicket.getStatus()).isEqualTo(TicketStatus.APPROVED);

        // Verify Order status updated to CONFIRMED
        OrderResponse confirmedOrder = orderService.getOrderById(orderRes.getId());
        assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    // -------------------------------------------------------------------------
    // AC-14: Ticket Rejection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-14: Ticket Rejection refunds stock and cancels Order")
    void testTicketRejectionFlow() {
        authenticateAs(customerUser);

        OrderCreationRequest orderReq = OrderCreationRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product1.getId()).quantity(3).build()))
                .build();

        OrderResponse orderRes = orderService.createOrder(orderReq);
        PurchaseTicket ticket = ticketRepository.findByOrderId(Long.valueOf(orderRes.getId())).orElseThrow();

        // Reject without comment must fail
        authenticateAs(poUser);
        TicketActionRequest emptyCommentReq = TicketActionRequest.builder().comment("   ").build();
        assertThatThrownBy(() -> ticketService.rejectTicket(ticket.getId(), emptyCommentReq))
                .isInstanceOf(BadRequestExeption.class);

        // Reject with valid comment
        TicketActionRequest validCommentReq = TicketActionRequest.builder()
                .comment("Out of stock or invalid request details").build();
        TicketResponse rejectedTicket = ticketService.rejectTicket(ticket.getId(), validCommentReq);
        assertThat(rejectedTicket.getStatus()).isEqualTo(TicketStatus.REJECTED);

        // Verify stock refunded and order cancelled
        Product refundedProduct = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(refundedProduct.getStock()).isEqualTo(10);

        OrderResponse cancelledOrder = orderService.getOrderById(orderRes.getId());
        assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // -------------------------------------------------------------------------
    // AC-22: SLA Expiration
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-22: SLA Scheduler auto-expires overdue ticket and refunds stock")
    void testSlaSchedulerExpiration() {
        authenticateAs(customerUser);

        OrderCreationRequest orderReq = OrderCreationRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product1.getId()).quantity(4).build()))
                .build();

        OrderResponse orderRes = orderService.createOrder(orderReq);
        PurchaseTicket ticket = ticketRepository.findByOrderId(Long.valueOf(orderRes.getId())).orElseThrow();

        // Backdate dueAt so it is already overdue — this saves and commits in its own TX
        ticket.setDueAt(LocalDateTime.now().minusHours(1));
        ticketRepository.saveAndFlush(ticket);

        // Trigger the SLA scheduler (inner REQUIRES_NEW TX can now see committed data)
        ticketSlaScheduler.processOverdueTickets();

        // Reload from DB and verify EXPIRED status and stock refund
        PurchaseTicket expiredTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(expiredTicket.getStatus()).isEqualTo(TicketStatus.EXPIRED);

        Product refundedProduct = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(refundedProduct.getStock()).isEqualTo(10);
    }
}
