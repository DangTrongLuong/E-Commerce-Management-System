package com.example.ecommerce.controller;

import com.example.ecommerce.order.dto.OrderCreationRequest;
import com.example.ecommerce.order.dto.OrderItemRequest;
import com.example.ecommerce.order.dto.OrderStatusUpdateRequest;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.order.dto.OrderTotalAmountResponse;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.common.exception.ConflictException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.order.service.OrderService;
import org.springframework.security.access.AccessDeniedException;
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
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/orders - Admin tạo đơn hàng thành công HTTP 201 CREATED")
    void createOrder_Admin_Returns201() throws Exception {
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
                .id(1002)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("3000.00"))
                .build();

        when(orderService.createOrders(any(OrderCreationRequest.class))).thenReturn(List.of(mockResponse));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1002))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
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

    // =========================================================================
    // TESTS FOR TODAY REVENUE APIS (ADMIN & PRODUCT OWNER)
    // =========================================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/orders/today-revenue - ADMIN lấy doanh thu hôm nay thành công HTTP 200")
    void admin_getTodayRevenue_Success_Returns200() throws Exception {
        OrderTotalAmountResponse mockResponse = OrderTotalAmountResponse.builder()
                .totalAmount(new BigDecimal("15000000.00"))
                .orderCount(25L)
                .date(java.time.LocalDate.now())
                .status(null)
                .currency("VND")
                .scope("ALL_SYSTEM")
                .build();

        when(orderService.getTotalOrderAmountForAdmin(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/orders/today-revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(15000000.00))
                .andExpect(jsonPath("$.data.orderCount").value(25))
                .andExpect(jsonPath("$.data.scope").value("ALL_SYSTEM"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/orders/today-revenue - ADMIN lấy doanh thu hôm nay có lọc status thành công HTTP 200")
    void admin_getTodayRevenue_WithStatus_Success_Returns200() throws Exception {
        OrderTotalAmountResponse mockResponse = OrderTotalAmountResponse.builder()
                .totalAmount(new BigDecimal("9000000.00"))
                .orderCount(15L)
                .date(java.time.LocalDate.now())
                .status(OrderStatus.PAID)
                .currency("VND")
                .scope("ALL_SYSTEM")
                .build();

        when(orderService.getTotalOrderAmountForAdmin(eq(OrderStatus.PAID), any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/orders/today-revenue?status=PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(9000000.00))
                .andExpect(jsonPath("$.data.orderCount").value(15))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/orders/today-revenue - USER gọi API doanh thu Admin bị chặn HTTP 403 kèm message rõ ràng")
    void admin_getTodayRevenue_ForbiddenForUser_Returns403() throws Exception {
        when(orderService.getTotalOrderAmountForAdmin(any(), any()))
                .thenThrow(new AccessDeniedException("Chỉ Quản trị viên (Admin) mới có quyền xem tổng tiền của toàn bộ hệ thống"));

        mockMvc.perform(get("/api/orders/today-revenue"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Chỉ Quản trị viên (Admin) mới có quyền xem tổng tiền của toàn bộ hệ thống"));
    }

    @Test
    @WithMockUser(roles = "PRODUCT_OWNER")
    @DisplayName("GET /api/orders/po/today-revenue - PO lấy doanh thu hôm nay của mình thành công HTTP 200")
    void po_getTodayRevenue_Success_Returns200() throws Exception {
        OrderTotalAmountResponse mockResponse = OrderTotalAmountResponse.builder()
                .totalAmount(new BigDecimal("4500000.00"))
                .orderCount(8L)
                .date(java.time.LocalDate.now())
                .status(null)
                .currency("VND")
                .scope("PRODUCT_OWNER")
                .poId(5L)
                .poEmail("po@shop.com")
                .build();

        when(orderService.getTotalOrderAmountForCurrentPo(any(), any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/orders/po/today-revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(4500000.00))
                .andExpect(jsonPath("$.data.orderCount").value(8))
                .andExpect(jsonPath("$.data.scope").value("PRODUCT_OWNER"))
                .andExpect(jsonPath("$.data.poId").value(5))
                .andExpect(jsonPath("$.data.poEmail").value("po@shop.com"));
    }

    @Test
    @WithMockUser(roles = "PRODUCT_OWNER")
    @DisplayName("GET /api/orders/po/today-revenue - PO lấy doanh thu hôm nay có lọc status thành công HTTP 200")
    void po_getTodayRevenue_WithStatus_Success_Returns200() throws Exception {
        OrderTotalAmountResponse mockResponse = OrderTotalAmountResponse.builder()
                .totalAmount(new BigDecimal("2000000.00"))
                .orderCount(3L)
                .date(java.time.LocalDate.now())
                .status(OrderStatus.COMPLETED)
                .currency("VND")
                .scope("PRODUCT_OWNER")
                .poId(5L)
                .poEmail("po@shop.com")
                .build();

        when(orderService.getTotalOrderAmountForCurrentPo(eq(OrderStatus.COMPLETED), any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/orders/po/today-revenue?status=COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(2000000.00))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/orders/po/today-revenue - USER gọi API doanh thu của PO bị chặn HTTP 403 kèm message rõ ràng")
    void po_getTodayRevenue_ForbiddenForUser_Returns403() throws Exception {
        when(orderService.getTotalOrderAmountForCurrentPo(any(), any()))
                .thenThrow(new AccessDeniedException("Chỉ Product Owner (PO) mới có quyền xem tổng tiền các đơn hàng của mình"));

        mockMvc.perform(get("/api/orders/po/today-revenue"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Chỉ Product Owner (PO) mới có quyền xem tổng tiền các đơn hàng của mình"));
    }

    @Test
    @DisplayName("GET /api/orders/today-revenue - Chưa đăng nhập bị từ chối")
    void todayRevenue_UnauthorizedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/orders/today-revenue"))
                .andExpect(status().is4xxClientError());
    }
}

