package com.example.ecommerce.integration;

import com.example.ecommerce.auth.dto.LoginRequest;
import com.example.ecommerce.auth.dto.RegisterRequest;
import com.example.ecommerce.auth.service.AuthService;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.common.exception.AccountLockedException;
import com.example.ecommerce.common.exception.BadRequestExeption;
import com.example.ecommerce.common.exception.DuplicateResourceException;
import com.example.ecommerce.common.exception.SelfApprovalNotAllowedException;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.customer.enums.CustomerStatus;
import com.example.ecommerce.customer.repository.CustomerRepository;
import com.example.ecommerce.notification.repository.NotificationRepository;
import com.example.ecommerce.order.dto.OrderCheckoutResponse;
import com.example.ecommerce.order.dto.OrderCreationRequest;
import com.example.ecommerce.order.dto.OrderItemRequest;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.entity.SubOrder;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.order.repository.SubOrderRepository;
import com.example.ecommerce.order.service.OrderService;
import com.example.ecommerce.order.service.OrderStatusStateMachine;
import com.example.ecommerce.payment.repository.PaymentTransactionRepository;
import com.example.ecommerce.common.exception.OrderNotCheckoutableException;
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
    @Autowired private OrderStatusStateMachine orderStatusStateMachine;
    @org.springframework.boot.test.mock.mockito.MockBean private com.example.ecommerce.notification.service.EmailService emailService;
    @org.springframework.boot.test.mock.mockito.MockBean private net.javacrumbs.shedlock.core.LockProvider lockProvider;

    // Repositories for setup & teardown
    @Autowired private AppUserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PurchaseTicketRepository ticketRepository;
    @Autowired private TicketHistoryRepository historyRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private SubOrderRepository subOrderRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;

    private AppUser adminUser;
    private AppUser poUser;
    private AppUser customerUser;
    private Customer customer;
    private Product product1;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        org.mockito.Mockito.when(lockProvider.lock(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(net.javacrumbs.shedlock.core.SimpleLock.class)));

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
        paymentTransactionRepository.deleteAll();
        // Order items and sub-orders are cascade-deleted with orders via orphanRemoval
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

        // Verify Order status is still PENDING (ready for checkout after approval)
        OrderResponse pendingOrder = orderService.getOrderById(orderRes.getId());
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // -------------------------------------------------------------------------
    // Level 2 Multi-Stage Approval: Orders >= 5M (PO -> Admin)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Đơn hàng >= 5 triệu: Luồng duyệt 1 cấp PENDING_APPROVAL -> APPROVED bởi PO, không cần admin")
    void testHighValueOrderMultiLevelApprovalFlow() {
        authenticateAs(customerUser);

        // Tạo đơn hàng 6 sản phẩm laptop (6 * 1.000.000 = 6.000.000 > 5 triệu)
        OrderCreationRequest orderReq = OrderCreationRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(product1.getId()).quantity(6).build()))
                .build();

        OrderResponse orderRes = orderService.createOrder(orderReq);
        assertThat(orderRes.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(orderRes.getTotalAmount()).isEqualByComparingTo("6000000.00");

        // 1. Kiểm tra vé được tạo với trạng thái ban đầu là PENDING_APPROVAL, requiredLevel = 1, currentLevel = 1
        PurchaseTicket ticket = ticketRepository.findByOrderId(Long.valueOf(orderRes.getId())).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING_APPROVAL);
        assertThat(ticket.getRequiredLevel()).isEqualTo(1);
        assertThat(ticket.getCurrentLevel()).isEqualTo(1);

        // 2. Separation of duties: Requester không được tự duyệt
        assertThatThrownBy(() -> ticketService.approveTicket(ticket.getId(), null))
                .isInstanceOf(SelfApprovalNotAllowedException.class);

        // 3. PO phụ trách duyệt vé 1 lần -> Chuyển sang APPROVED
        authenticateAs(poUser);
        TicketResponse poApprovedTicket = ticketService.approveTicket(ticket.getId(), null);
        assertThat(poApprovedTicket.getStatus()).isEqualTo(TicketStatus.APPROVED);

        // 4. Verify trạng thái cuối cùng
        PurchaseTicket finalTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(finalTicket.getStatus()).isEqualTo(TicketStatus.APPROVED);
        assertThat(finalTicket.getDecidedAt()).isNotNull();
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

    // -------------------------------------------------------------------------
    // Order Item Modification -> Ticket Sync
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Sync Ticket: Modifying order items updates ticket status and approval levels")
    void testOrderModification_SyncsTicketStatusAndLevels() {
        authenticateAs(customerUser);

        // Tạo 1 sản phẩm giá rẻ (100.000 VNĐ)
        Product cheapProduct = productRepository.save(Product.builder()
                .name("Cable")
                .price(new BigDecimal("100000.00"))
                .stock(20)
                .status(ProductStatus.ACTIVE)
                .owner(poUser)
                .build());

        // 1. Tạo đơn hàng 1 item: 100k (< 500k) -> Ticket APPROVED, level 0
        OrderCreationRequest orderReq = OrderCreationRequest.builder()
                .customerId(customer.getId())
                .items(List.of(OrderItemRequest.builder()
                        .productId(cheapProduct.getId()).quantity(1).build()))
                .build();

        OrderResponse orderRes = orderService.createOrder(orderReq);
        PurchaseTicket ticket = ticketRepository.findByOrderId(Long.valueOf(orderRes.getId())).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.APPROVED);
        assertThat(ticket.getRequiredLevel()).isEqualTo(0);

        // 2. Thêm sản phẩm: 2 Laptop (giá 1M mỗi cái) -> Tổng tiền 2.1M (500k - < 5M)
        // -> Ticket tự động chuyển sang PENDING_APPROVAL, requiredLevel = 1
        orderService.addItemToOrder(orderRes.getId(), OrderItemRequest.builder()
                .productId(product1.getId()).quantity(2).build());

        PurchaseTicket ticketAfterAdd = ticketRepository.findByOrderId(Long.valueOf(orderRes.getId())).orElseThrow();
        assertThat(ticketAfterAdd.getStatus()).isEqualTo(TicketStatus.PENDING_APPROVAL);
        assertThat(ticketAfterAdd.getRequiredLevel()).isEqualTo(1);
        assertThat(ticketAfterAdd.getCurrentLevel()).isEqualTo(1);

        // PO xem hòm thư inbox phải thấy vé này!
        authenticateAs(poUser);
        PageResponse<TicketResponse> poInbox = ticketService.getInboxTickets(null, 0, 10);
        assertThat(poInbox.getContent().stream().anyMatch(t -> t.getId().equals(ticket.getId()))).isTrue();

        // 3. Khách hàng tăng số lượng Laptop lên 6 cái (giá 6M + 100k = 6.1M >= 5M)
        // -> Ticket vẫn giữ PENDING_APPROVAL, requiredLevel = 1 (chỉ cần PO duyệt)
        authenticateAs(customerUser);
        orderService.updateOrderItemQuantity(orderRes.getId(), product1.getId(),
                com.example.ecommerce.order.dto.UpdateOrderItemQuantityRequest.builder().quantity(6).build());

        PurchaseTicket ticketAfterIncrease = ticketRepository.findByOrderId(Long.valueOf(orderRes.getId())).orElseThrow();
        assertThat(ticketAfterIncrease.getStatus()).isEqualTo(TicketStatus.PENDING_APPROVAL);
        assertThat(ticketAfterIncrease.getRequiredLevel()).isEqualTo(1);
        assertThat(ticketAfterIncrease.getCurrentLevel()).isEqualTo(1);

        // PO vẫn thấy trong inbox ở cấp 1!
        authenticateAs(poUser);
        PageResponse<TicketResponse> poInboxLevel2 = ticketService.getInboxTickets(null, 0, 10);
        assertThat(poInboxLevel2.getContent().stream().anyMatch(t -> t.getId().equals(ticket.getId()))).isTrue();

        // 4. Khách hàng xóa bớt Laptop, chỉ giữ lại Cable (100k < 500k)
        // -> Ticket tự động chuyển về APPROVED, requiredLevel = 0
        authenticateAs(customerUser);
        orderService.deleteOrderItem(orderRes.getId(), product1.getId());

        PurchaseTicket ticketAfterDelete = ticketRepository.findByOrderId(Long.valueOf(orderRes.getId())).orElseThrow();
        assertThat(ticketAfterDelete.getStatus()).isEqualTo(TicketStatus.APPROVED);
        assertThat(ticketAfterDelete.getRequiredLevel()).isEqualTo(0);
        assertThat(ticketAfterDelete.getCurrentLevel()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Multi-PO Order: Sequential order IDs, Sub-orders with code .1/.2, and conditional checkout
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Multi-PO Order: Sequential order IDs, Sub-orders with code .1/.2, and conditional checkout")
    void testMultiPoOrderSequentialIdAndConditionalCheckout() {
        // 1. Setup a second PO and their product
        AppUser poUser2 = userRepository.save(AppUser.builder()
                .email("po2_test@example.com")
                .passwordHash("password_hash")
                .role(Role.PRODUCT_OWNER)
                .status(UserStatus.ACTIVE)
                .build());

        Product product2 = productRepository.save(Product.builder()
                .name("Test Monitor PO2")
                .price(new BigDecimal("2000000.00"))
                .stock(10)
                .status(ProductStatus.ACTIVE)
                .owner(poUser2)
                .build());

        authenticateAs(customerUser);

        // 2. Customer creates an order containing items from 2 different POs
        OrderCreationRequest multiPoReq = OrderCreationRequest.builder()
                .customerId(customer.getId())
                .items(List.of(
                        OrderItemRequest.builder().productId(product1.getId()).quantity(1).build(), // PO 1: 1M
                        OrderItemRequest.builder().productId(product2.getId()).quantity(1).build()  // PO 2: 2M
                ))
                .build();

        OrderResponse orderRes1 = orderService.createOrder(multiPoReq);
        Integer order1Id = orderRes1.getId();

        // Verify: exactly 1 order row in orders table
        assertThat(orderRes1.getSubOrders()).hasSize(2);
        List<SubOrder> subOrders1 = subOrderRepository.findByOrderId(order1Id);
        assertThat(subOrders1).hasSize(2);
        assertThat(subOrders1).extracting(SubOrder::getSubOrderCode)
                .containsExactlyInAnyOrder(order1Id + ".1", order1Id + ".2");

        // 3. Customer creates a second order immediately after
        OrderCreationRequest nextOrderReq = OrderCreationRequest.builder()
                .customerId(customer.getId())
                .items(List.of(
                        OrderItemRequest.builder().productId(product1.getId()).quantity(1).build()
                ))
                .build();

        OrderResponse orderRes2 = orderService.createOrder(nextOrderReq);
        Integer order2Id = orderRes2.getId();

        // VERIFY STRICT SEQUENTIAL ORDER ID: order2Id == order1Id + 1 (no gaps caused by child orders!)
        assertThat(order2Id).isEqualTo(order1Id + 1);

        // 4. Verify Payment Rules on Order 1
        // Both PO sub-orders are >= 500k, so their tickets are PENDING_APPROVAL
        SubOrder subOrder1 = subOrders1.stream()
                .filter(s -> s.getPoOwner().getId().equals(poUser.getId()))
                .findFirst().orElseThrow();
        SubOrder subOrder2 = subOrders1.stream()
                .filter(s -> s.getPoOwner().getId().equals(poUser2.getId()))
                .findFirst().orElseThrow();

        PurchaseTicket ticketSub1 = ticketRepository.findBySubOrderId(subOrder1.getId()).orElseThrow();
        PurchaseTicket ticketSub2 = ticketRepository.findBySubOrderId(subOrder2.getId()).orElseThrow();
        assertThat(ticketSub1.getStatus()).isEqualTo(TicketStatus.PENDING_APPROVAL);
        assertThat(ticketSub2.getStatus()).isEqualTo(TicketStatus.PENDING_APPROVAL);

        org.springframework.mock.web.MockHttpServletRequest httpReq = new org.springframework.mock.web.MockHttpServletRequest();
        httpReq.setRemoteAddr("127.0.0.1");

        // A) Customer attempts full checkout -> MUST FAIL because tickets are not approved
        assertThatThrownBy(() -> orderService.checkoutOrder(order1Id, "idem-full-fail", null, httpReq))
                .isInstanceOf(OrderNotCheckoutableException.class)
                .hasMessageContaining("chưa được phê duyệt vé mua hàng");

        // B) Customer attempts checkout for subOrder1 individually -> MUST FAIL because ticketSub1 is not approved
        assertThatThrownBy(() -> orderService.checkoutSubOrder(order1Id, subOrder1.getId(), "idem-sub1-fail", null, httpReq))
                .isInstanceOf(OrderNotCheckoutableException.class);

        // C) PO 1 approves ticket for subOrder1
        authenticateAs(poUser);
        ticketService.approveTicket(ticketSub1.getId(), TicketActionRequest.builder().comment("PO 1 approved for production").build());
        PurchaseTicket approvedTicket1 = ticketRepository.findById(ticketSub1.getId()).orElseThrow();
        assertThat(approvedTicket1.getStatus()).isEqualTo(TicketStatus.APPROVED);

        // D) Customer attempts full checkout again -> STILL FAILS because subOrder2 is still pending!
        authenticateAs(customerUser);
        assertThatThrownBy(() -> orderService.checkoutOrder(order1Id, "idem-full-still-fail", null, httpReq))
                .isInstanceOf(OrderNotCheckoutableException.class)
                .hasMessageContaining(subOrder2.getSubOrderCode());

        // E) Customer checks out subOrder1 individually -> SUCCEEDS!
        OrderCheckoutResponse checkoutSub1 = orderService.checkoutSubOrder(order1Id, subOrder1.getId(), "idem-sub1-ok", null, httpReq);
        assertThat(checkoutSub1.getPaymentUrl()).isNotBlank();
        assertThat(checkoutSub1.getSubOrderId()).isEqualTo(subOrder1.getId());
        assertThat(checkoutSub1.getSubOrderCode()).isEqualTo(subOrder1.getSubOrderCode());

        SubOrder updatedSub1 = subOrderRepository.findById(subOrder1.getId()).orElseThrow();
        assertThat(updatedSub1.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);

        // F) PO 2 approves ticket for subOrder2
        authenticateAs(poUser2);
        ticketService.approveTicket(ticketSub2.getId(), TicketActionRequest.builder().comment("PO 2 approved for shipment").build());

        // G) Customer checks out subOrder2 individually -> SUCCEEDS!
        authenticateAs(customerUser);
        OrderCheckoutResponse checkoutSub2 = orderService.checkoutSubOrder(order1Id, subOrder2.getId(), "idem-sub2-ok", null, httpReq);
        assertThat(checkoutSub2.getPaymentUrl()).isNotBlank();
        assertThat(checkoutSub2.getSubOrderId()).isEqualTo(subOrder2.getId());

        // H) Simulate payment completion: when both sub-orders are PAID, master Order ascends to PAID!
        orderStatusStateMachine.transitionSubOrder(updatedSub1, OrderStatus.PAID, "SYSTEM", "Payment completed via VNPay");
        SubOrder updatedSub2 = subOrderRepository.findById(subOrder2.getId()).orElseThrow();
        orderStatusStateMachine.transitionSubOrder(updatedSub2, OrderStatus.PAID, "SYSTEM", "Payment completed via VNPay");

        Order finalMasterOrder = orderRepository.findById(order1Id).orElseThrow();
        assertThat(finalMasterOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(finalMasterOrder.getPaymentStatus()).isEqualTo(com.example.ecommerce.order.enums.OrderPaymentStatus.PAID);
    }
}
