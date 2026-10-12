package com.example.ecommerce.integration;

import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.common.exception.EmailNotResendableException;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.customer.enums.CustomerStatus;
import com.example.ecommerce.customer.repository.CustomerRepository;
import com.example.ecommerce.notification.controller.AdminEmailController;
import com.example.ecommerce.notification.dto.EmailOutboxResponse;
import com.example.ecommerce.notification.entity.EmailOutbox;
import com.example.ecommerce.notification.enums.EmailOutboxStatus;
import com.example.ecommerce.notification.repository.EmailOutboxRepository;
import com.example.ecommerce.notification.service.EmailOutboxService;
import com.example.ecommerce.notification.service.EmailOutboxWorker;
import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.entity.OrderItem;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.order.service.OrderStatusStateMachine;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.product.enums.ProductStatus;
import com.example.ecommerce.product.repository.ProductRepository;
import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.user.enums.Role;
import com.example.ecommerce.user.enums.UserStatus;
import com.example.ecommerce.user.repository.AppUserRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class Req03EmailOutboxIntegrationTest {

    @Autowired private OrderStatusStateMachine stateMachine;
    @Autowired private EmailOutboxService emailOutboxService;
    @Autowired private EmailOutboxWorker emailOutboxWorker;
    @Autowired private AdminEmailController adminEmailController;
    @Autowired private EmailOutboxRepository emailOutboxRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AppUserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private JavaMailSender mailSender;
    @MockBean private net.javacrumbs.shedlock.core.LockProvider lockProvider;

    private Customer testCustomer;
    private Product testProduct;
    private AppUser adminUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        Mockito.when(lockProvider.lock(any()))
                .thenReturn(Optional.of(Mockito.mock(net.javacrumbs.shedlock.core.SimpleLock.class)));

        MimeMessage mockMimeMessage = Mockito.mock(MimeMessage.class);
        Mockito.when(mailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        emailOutboxRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(AppUser.builder()
                .email("admin@shop.com")
                .passwordHash("hashed")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        testCustomer = customerRepository.save(Customer.builder()
                .name("Nguyễn Văn An")
                .email("an.nguyen@example.com")
                .phone("0901234567")
                .status(CustomerStatus.ACTIVE)
                .build());

        testProduct = productRepository.save(Product.builder()
                .name("iPhone 16 128GB")
                .price(new BigDecimal("22990000"))
                .stock(10)
                .status(ProductStatus.ACTIVE)
                .build());

        authenticateAs(adminUser);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(emailOutboxService, "failAfterPaid", false);
        emailOutboxRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        SecurityContextHolder.clearContext();
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

    private Order createTestOrder() {
        Order order = Order.builder()
                .customer(testCustomer)
                .status(OrderStatus.AWAITING_PAYMENT)
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .product(testProduct)
                .quantity(2)
                .unitPrice(testProduct.getPrice())
                .subTotal(new BigDecimal("45980000"))
                .build();

        order.addItems(item);
        order.setTotalAmount(new BigDecimal("45980000"));
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("REQ-03 Kịch bản 1: Đơn hàng chuyển PAID -> Tự động sinh EMAIL_OUTBOX (ORDER_PAID) trong cùng transaction")
    void testOrderPaid_GeneratesEmailOutbox() {
        Order order = createTestOrder();

        // Chuyển sang PAID
        transactionTemplate.executeWithoutResult(status -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            stateMachine.transition(o, OrderStatus.PAID, "TEST", "Payment verified");
            orderRepository.save(o);
        });

        // Kiểm tra Order đã PAID và paidAt có giá trị
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(updatedOrder.getPaidAt()).isNotNull();

        // Kiểm tra EMAIL_OUTBOX
        Optional<EmailOutbox> outboxOpt = emailOutboxRepository.findByTemplateAndReferenceId("ORDER_PAID", Long.valueOf(order.getId()));
        assertThat(outboxOpt).isPresent();

        EmailOutbox outbox = outboxOpt.get();
        assertThat(outbox.getRecipient()).isEqualTo("an.nguyen@example.com");
        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(outbox.getAttempts()).isEqualTo(0);
        assertThat(outbox.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("REQ-03 Kịch bản 2: Gặp callback lặp lại cho đơn đã PAID -> Vẫn chỉ 1 dòng EMAIL_OUTBOX")
    void testRepeatCallback_DoesNotDuplicateOutbox() {
        Order order = createTestOrder();

        transactionTemplate.executeWithoutResult(status -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            stateMachine.transition(o, OrderStatus.PAID, "TEST", "Payment verified 1");
            orderRepository.save(o);
        });

        // Callback lặp lại
        transactionTemplate.executeWithoutResult(status -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            stateMachine.transition(o, OrderStatus.PAID, "TEST", "Payment verified 2");
            orderRepository.save(o);
        });

        List<EmailOutbox> outboxes = emailOutboxRepository.findAll();
        assertThat(outboxes).hasSize(1);
    }

    @Test
    @DisplayName("REQ-03 Kịch bản 3: Đơn hàng bị huỷ -> Không có dòng EMAIL_OUTBOX (ORDER_PAID)")
    void testCancelledOrder_NoOutboxGenerated() {
        Order order = createTestOrder();

        transactionTemplate.executeWithoutResult(status -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            stateMachine.transition(o, OrderStatus.CANCELLED, "TEST", "PAYMENT_FAILED");
            orderRepository.save(o);
        });

        List<EmailOutbox> outboxes = emailOutboxRepository.findAll();
        assertThat(outboxes).isEmpty();
    }

    @Test
    @DisplayName("REQ-03 Kịch bản 4 & 5: Worker quét và gửi mail thành công -> Chuyển SENT, ghi sentAt")
    void testWorker_SendsEmailSuccessfully() {
        Order order = createTestOrder();

        transactionTemplate.executeWithoutResult(status -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            stateMachine.transition(o, OrderStatus.PAID, "TEST", "Payment verified");
            orderRepository.save(o);
        });

        // Chạy worker
        emailOutboxWorker.processPendingEmails();

        EmailOutbox outbox = emailOutboxRepository.findByTemplateAndReferenceId("ORDER_PAID", Long.valueOf(order.getId())).orElseThrow();
        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
        assertThat(outbox.getLastError()).isNull();

        Mockito.verify(mailSender, Mockito.atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("REQ-03 Kịch bản 4 & 5: Worker gặp lỗi gửi mail -> Tăng ATTEMPTS, ghi LAST_ERROR, lùi NEXT_ATTEMPT_AT")
    void testWorker_HandlesFailureWithExponentialBackoff() {
        doThrow(new MailSendException("SMTP connection refused: Mail server down"))
                .when(mailSender).send(any(MimeMessage.class));

        Order order = createTestOrder();

        transactionTemplate.executeWithoutResult(status -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            stateMachine.transition(o, OrderStatus.PAID, "TEST", "Payment verified");
            orderRepository.save(o);
        });

        // Chạy worker lần 1 (lỗi)
        LocalDateTime beforeRun = LocalDateTime.now();
        emailOutboxWorker.processPendingEmails();

        EmailOutbox outbox = emailOutboxRepository.findByTemplateAndReferenceId("ORDER_PAID", Long.valueOf(order.getId())).orElseThrow();
        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(outbox.getAttempts()).isEqualTo(1);
        assertThat(outbox.getLastError()).contains("SMTP connection refused");
        assertThat(outbox.getNextAttemptAt()).isAfter(beforeRun);
    }

    @Test
    @DisplayName("REQ-03 Kịch bản 6: Sau 5 lần thử thất bại -> Chuyển STATUS = FAILED, xuất hiện trong API admin")
    void testWorker_FailsAfter5Attempts_AppearsInAdminApi() {
        doThrow(new MailSendException("SMTP server unavailable"))
                .when(mailSender).send(any(MimeMessage.class));

        Order order = createTestOrder();

        transactionTemplate.executeWithoutResult(status -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            stateMachine.transition(o, OrderStatus.PAID, "TEST", "Payment verified");
            orderRepository.save(o);
        });

        // Giả lập đã thử 4 lần trước đó
        EmailOutbox outbox = emailOutboxRepository.findByTemplateAndReferenceId("ORDER_PAID", Long.valueOf(order.getId())).orElseThrow();
        outbox.setAttempts(4);
        outbox.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        emailOutboxRepository.save(outbox);

        // Chạy worker lần 5 -> sẽ đạt 5 attempts -> FAILED
        emailOutboxWorker.processPendingEmails();

        EmailOutbox failedOutbox = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(failedOutbox.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(failedOutbox.getAttempts()).isEqualTo(5);

        // Kiểm tra GET /api/admin/emails?status=FAILED
        PageResponse<EmailOutboxResponse> adminList = adminEmailController.getEmails(EmailOutboxStatus.FAILED, 0, 20).getBody().getData();
        assertThat(adminList.getContent()).hasSize(1);
        assertThat(adminList.getContent().get(0).getId()).isEqualTo(failedOutbox.getId());
    }

    @Test
    @DisplayName("REQ-03 Kịch bản 7: Gọi resend email FAILED -> PENDING, attempts=0; Gửi lại email không ở FAILED -> 409")
    void testAdminResendEmail() {
        Order order = createTestOrder();

        EmailOutbox outbox = emailOutboxRepository.save(EmailOutbox.builder()
                .template("ORDER_PAID")
                .referenceId(Long.valueOf(order.getId()))
                .recipient("an.nguyen@example.com")
                .status(EmailOutboxStatus.FAILED)
                .attempts(5)
                .lastError("Test failure")
                .nextAttemptAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build());

        // Gọi resend
        adminEmailController.resendEmail(outbox.getId());

        EmailOutbox resendOutbox = emailOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(resendOutbox.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(resendOutbox.getAttempts()).isEqualTo(0);

        // Thử resend khi đã PENDING -> Bị từ chối 409 EMAIL_NOT_RESENDABLE
        assertThatThrownBy(() -> adminEmailController.resendEmail(outbox.getId()))
                .isInstanceOf(EmailNotResendableException.class);
    }

    @Test
    @DisplayName("REQ-03 Kịch bản 8: Bật cờ fail-after-paid -> Đơn hàng không chuyển PAID và không có dòng outbox")
    void testFailAfterPaid_RollsBackTransaction() {
        Order order = createTestOrder();

        // Bật cờ fail-after-paid
        ReflectionTestUtils.setField(emailOutboxService, "failAfterPaid", true);

        // Cố gắng chuyển sang PAID trong transaction
        assertThatThrownBy(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                Order o = orderRepository.findById(order.getId()).orElseThrow();
                stateMachine.transition(o, OrderStatus.PAID, "TEST", "Payment verified");
                orderRepository.save(o);
            });
        }).isInstanceOf(RuntimeException.class);

        // Kiểm tra Order KHÔNG chuyển sang PAID
        Order checkOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(checkOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);

        // Kiểm tra EMAIL_OUTBOX không có bản ghi nào (đã rollback hoàn toàn)
        List<EmailOutbox> outboxList = emailOutboxRepository.findAll();
        assertThat(outboxList).isEmpty();
    }
}
