package com.example.ecommerce.integration;

import com.example.ecommerce.cart.dto.AddToCartRequest;
import com.example.ecommerce.cart.dto.CartResponse;
import com.example.ecommerce.cart.dto.UpdateCartItemRequest;
import com.example.ecommerce.cart.service.CartService;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.customer.enums.CustomerStatus;
import com.example.ecommerce.customer.repository.CustomerRepository;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.product.enums.ProductStatus;
import com.example.ecommerce.product.repository.ProductRepository;
import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.user.enums.Role;
import com.example.ecommerce.user.enums.UserStatus;
import com.example.ecommerce.user.repository.AppUserRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class CartIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    private Customer testCustomer;
    private AppUser testUser;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        Customer newCustomer = Customer.builder()
                .name("Cart Tester")
                .email("carttester_" + uniqueSuffix + "@gmail.com")
                .phone("098" + uniqueSuffix.substring(uniqueSuffix.length() - 7))
                .status(CustomerStatus.ACTIVE)
                .build();

        testUser = userRepository.saveAndFlush(AppUser.builder()
                .email(newCustomer.getEmail())
                .passwordHash("hashedPass123@")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .customer(newCustomer)
                .tokenVersion(1)
                .build());
        testCustomer = testUser.getCustomer();

        testProduct = productRepository.save(Product.builder()
                .name("Test iPhone 16 Pro")
                .price(BigDecimal.valueOf(25000000))
                .stock(50)
                .status(ProductStatus.ACTIVE)
                .build());

        // Authenticate
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        testUser.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Test Cart full lifecycle: Add -> Get -> Update -> Remove -> Clear")
    void testCartLifecycle() {
        // 1. Initial cart is empty
        CartResponse initialCart = cartService.getCart();
        assertThat(initialCart).isNotNull();
        assertThat(initialCart.getTotalItems()).isEqualTo(0);

        // 2. Add to cart
        CartResponse afterAdd = cartService.addToCart(AddToCartRequest.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build());
        assertThat(afterAdd.getItems()).hasSize(1);
        assertThat(afterAdd.getTotalItems()).isEqualTo(2);
        assertThat(afterAdd.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000000));

        // 3. Add same item again (quantity merges)
        CartResponse afterAddMore = cartService.addToCart(AddToCartRequest.builder()
                .productId(testProduct.getId())
                .quantity(1)
                .build());
        assertThat(afterAddMore.getItems()).hasSize(1);
        assertThat(afterAddMore.getTotalItems()).isEqualTo(3);
        assertThat(afterAddMore.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(75000000));

        // 4. Update quantity
        CartResponse afterUpdate = cartService.updateItemQuantity(testProduct.getId(), UpdateCartItemRequest.builder()
                .quantity(5)
                .build());
        assertThat(afterUpdate.getItems().get(0).getQuantity()).isEqualTo(5);
        assertThat(afterUpdate.getTotalItems()).isEqualTo(5);

        // 5. Remove item
        CartResponse afterRemove = cartService.removeItem(testProduct.getId());
        assertThat(afterRemove.getItems()).isEmpty();
        assertThat(afterRemove.getTotalItems()).isEqualTo(0);

        // 6. Add and clear
        cartService.addToCart(AddToCartRequest.builder()
                .productId(testProduct.getId())
                .quantity(1)
                .build());
        CartResponse afterClear = cartService.clearCart();
        assertThat(afterClear.getItems()).isEmpty();
        assertThat(afterClear.getTotalItems()).isEqualTo(0);
    }
}


