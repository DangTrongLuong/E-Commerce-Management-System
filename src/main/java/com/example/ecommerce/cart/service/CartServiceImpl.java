package com.example.ecommerce.cart.service;

import com.example.ecommerce.cart.dto.AddToCartRequest;
import com.example.ecommerce.cart.dto.CartItemResponse;
import com.example.ecommerce.cart.dto.CartResponse;
import com.example.ecommerce.cart.dto.UpdateCartItemRequest;
import com.example.ecommerce.cart.entity.Cart;
import com.example.ecommerce.cart.entity.CartItem;
import com.example.ecommerce.cart.repository.CartItemRepository;
import com.example.ecommerce.cart.repository.CartRepository;
import com.example.ecommerce.common.exception.BadRequestExeption;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.common.util.SecurityUtils;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.product.enums.ProductStatus;
import com.example.ecommerce.product.repository.ProductRepository;
import com.example.ecommerce.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final SecurityUtils securityUtils;

    private Customer getCurrentCustomer() {
        AppUser user = securityUtils.getCurrentUser();
        if (user.getCustomer() == null) {
            throw new BadRequestExeption("Tài khoản chưa được liên kết với thông tin khách hàng");
        }
        return user.getCustomer();
    }

    private Cart getOrCreateCart(Customer customer) {
        return cartRepository.findByCustomerIdWithItems(customer.getId())
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .customer(customer)
                            .items(new ArrayList<>())
                            .build();
                    return cartRepository.save(newCart);
                });
    }

    @Override
    @Transactional
    public CartResponse getCart() {
        Customer customer = getCurrentCustomer();
        Cart cart = getOrCreateCart(customer);
        return mapToCartResponse(cart);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartResponse addToCart(AddToCartRequest request) {
        Customer customer = getCurrentCustomer();
        Cart cart = getOrCreateCart(customer);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + request.getProductId()));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BadRequestExeption("Sản phẩm hiện không mở bán");
        }

        if (product.getStock() <= 0) {
            throw new BadRequestExeption("Sản phẩm đã hết hàng");
        }

        Optional<CartItem> existingItemOpt = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId());

        if (existingItemOpt.isPresent()) {
            CartItem item = existingItemOpt.get();
            int newQuantity = item.getQuantity() + request.getQuantity();
            if (newQuantity > 99) {
                newQuantity = 99;
            }
            if (newQuantity > product.getStock()) {
                throw new BadRequestExeption("Số lượng trong giỏ hàng (" + newQuantity + ") vượt quá tồn kho (" + product.getStock() + ")");
            }
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        } else {
            if (request.getQuantity() > product.getStock()) {
                throw new BadRequestExeption("Số lượng yêu cầu (" + request.getQuantity() + ") vượt quá tồn kho (" + product.getStock() + ")");
            }
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cart.addItem(newItem);
            cartItemRepository.save(newItem);
        }

        Cart refreshedCart = cartRepository.findByCustomerIdWithItems(customer.getId()).orElse(cart);
        return mapToCartResponse(refreshedCart);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartResponse updateItemQuantity(Integer productId, UpdateCartItemRequest request) {
        Customer customer = getCurrentCustomer();
        Cart cart = getOrCreateCart(customer);

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            cart.getItems().removeIf(item -> item.getProduct() != null && item.getProduct().getId() == productId);
            Cart savedCart = cartRepository.save(cart);
            return mapToCartResponse(savedCart);
        } else {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));

            if (request.getQuantity() > product.getStock()) {
                throw new BadRequestExeption("Số lượng yêu cầu (" + request.getQuantity() + ") vượt quá tồn kho (" + product.getStock() + ")");
            }

            CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không có trong giỏ hàng"));

            item.setQuantity(request.getQuantity());
            cartItemRepository.save(item);
        }

        Cart refreshedCart = cartRepository.findByCustomerIdWithItems(customer.getId()).orElse(cart);
        return mapToCartResponse(refreshedCart);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartResponse removeItem(Integer productId) {
        Customer customer = getCurrentCustomer();
        Cart cart = getOrCreateCart(customer);

        cart.getItems().removeIf(item -> item.getProduct() != null && item.getProduct().getId() == productId);
        Cart savedCart = cartRepository.save(cart);

        return mapToCartResponse(savedCart);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartResponse clearCart() {
        Customer customer = getCurrentCustomer();
        Cart cart = getOrCreateCart(customer);

        cart.getItems().clear();
        Cart savedCart = cartRepository.save(cart);

        return mapToCartResponse(savedCart);
    }

    private CartResponse mapToCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalItems = 0;

        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                Product product = item.getProduct();
                if (product == null) continue;

                BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                totalAmount = totalAmount.add(subtotal);
                totalItems += item.getQuantity();

                itemResponses.add(CartItemResponse.builder()
                        .id(item.getId())
                        .productId(product.getId())
                        .productName(product.getName())
                        .imageUrl(product.getImageUrl())
                        .price(product.getPrice())
                        .quantity(item.getQuantity())
                        .stock(product.getStock())
                        .subtotal(subtotal)
                        .build());
            }
        }

        return CartResponse.builder()
                .id(cart.getId())
                .customerId(cart.getCustomer() != null ? cart.getCustomer().getId() : null)
                .items(itemResponses)
                .totalItems(totalItems)
                .totalAmount(totalAmount)
                .build();
    }
}
