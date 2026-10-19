package com.example.ecommerce.order.mapper;

import com.example.ecommerce.order.dto.OrderItemResponse;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.entity.OrderItem;
import com.example.ecommerce.order.dto.SubOrderResponse;
import com.example.ecommerce.order.entity.SubOrder;
import com.example.ecommerce.user.entity.AppUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer.name")
    @Mapping(target = "customerEmail", source = "customer.email")
    @Mapping(target = "customerPhone", source = "customer.phone")
    @Mapping(target = "items", source = "orderItems")
    @Mapping(target = "parentId", source = "parentOrder.id")
    @Mapping(target = "subOrders", source = "subOrders")
    OrderResponse toResponse(Order order);

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "poId", source = "poOwner.id")
    @Mapping(target = "poName", source = "poOwner", qualifiedByName = "getPoOwnerName")
    @Mapping(target = "items", source = "orderItems")
    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "ticketStatus", source = "ticket.status")
    SubOrderResponse toSubOrderResponse(SubOrder subOrder);

    List<SubOrderResponse> toSubOrderResponseList(List<SubOrder> subOrders);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "imageUrl", source = "product.imageUrl")
    OrderItemResponse toItemResponse(OrderItem orderItem);

    List<OrderItemResponse> toItemResponseList(List<OrderItem> orderItems);

    @Named("getPoOwnerName")
    default String getPoOwnerName(AppUser poOwner) {
        if (poOwner == null) return null;
        if (poOwner.getCustomer() != null && poOwner.getCustomer().getName() != null) {
            return poOwner.getCustomer().getName();
        }
        return poOwner.getEmail();
    }
}

