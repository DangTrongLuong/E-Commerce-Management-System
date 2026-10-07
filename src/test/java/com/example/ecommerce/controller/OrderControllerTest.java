package com.example.ecommerce.controller;

import com.example.ecommerce.order.dto.OrderCreationRequest;
import com.example.ecommerce.order.dto.OrderItemRequest;
import com.example.ecommerce.order.dto.OrderStatusUpdateRequest;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.common.exception.ConflictException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private com.example.ecommerce.notification.service.EmailService emailService;

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /api/orders - Tạo đơn hàng thành công HTTP 201 CREATED")
    void createOrder_Success_Returns201() throws Exception {
        OrderCreationRequest request = OrderCreationRequest.builder()
                .customerId(1)
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(101)
                                .quantity(2)
                                .build()
                ))
                .build();

        OrderResponse mockResponse = OrderResponse.builder()
                .id(1001)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("3000.00"))
                .build();

        when(orderService.createOrders(any(OrderCreationRequest.class))).thenReturn(List.of(mockResponse));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1001))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].totalAmount").value(3000.00));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /api/orders - Thất bại khi thiếu tồn kho (insufficientStock) HTTP 409 CONFLCT")
    void createOrder_InsufficientStock_Returns409() throws Exception {
        OrderCreationRequest request = OrderCreationRequest.builder()
                .customerId(1)
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(101)
                                .quantity(50)
                                .build()
                ))
                .build();

        when(orderService.createOrders(any(OrderCreationRequest.class)))
                .thenThrow(new ConflictException("Sản phẩm không đủ tồn kho."));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Sản phẩm không đủ tồn kho."));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /api/orders - Thất bại khi không tìm thấy khách hàng HTTP 404 NOT_FOUND")
    void createOrder_CustomerNotFound_Returns404() throws Exception {
        OrderCreationRequest request = OrderCreationRequest.builder()
                .customerId(999)
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(101)
                                .quantity(1)
                                .build()
                ))
                .build();

        when(orderService.createOrders(any(OrderCreationRequest.class)))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy khách hàng"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy khách hàng"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PATCH /api/orders/{id}/status - Cập nhật trạng thái đơn hàng thành công HTTP 200")
    void updateOrderStatus_Success_Returns200() throws Exception {
        OrderStatusUpdateRequest updateRequest = OrderStatusUpdateRequest.builder()
                .orderStatus(OrderStatus.CONFIRMED)
                .build();

        OrderResponse mockResponse = OrderResponse.builder()
                .id(1001)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("3000.00"))
                .build();

        when(orderService.updateOrderStatus(eq(1001), any(OrderStatusUpdateRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(patch("/api/orders/1001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }
}
