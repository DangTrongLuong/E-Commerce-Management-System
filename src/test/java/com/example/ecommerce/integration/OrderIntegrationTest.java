package com.example.ecommerce.integration;

import com.example.ecommerce.order.dto.OrderCreationRequest;
import com.example.ecommerce.order.dto.OrderItemRequest;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.product.enums.ProductStatus;
import com.example.ecommerce.customer.repository.CustomerRepository;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.product.repository.ProductRepository;
import com.example.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrderIntegrationTest {

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("ecom_test_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private OrderService orderService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.ecommerce.notification.service.EmailService emailService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Customer testCustomer;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();

        testCustomer = customerRepository.save(Customer.builder()
                .name("Integration Test User")
                .email("integration@example.com")
                .phone("0123456789")
                .build());

        testProduct = productRepository.save(Product.builder()
                .name("Test Laptop")
                .price(new BigDecimal("1000.00"))
                .stock(20)
                .status(ProductStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("Kiểm thử tích hợp End-to-End: Tạo đơn hàng thành công với MySQL Testcontainers")
    void createOrder_IntegrationSuccess() {
        OrderCreationRequest request = OrderCreationRequest.builder()
                .customerId(testCustomer.getId())
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(testProduct.getId())
                                .quantity(3)
                                .build()
                ))
                .build();

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("3000.00");

        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(17);

        assertThat(orderRepository.findAll()).hasSize(1);
    }
}
