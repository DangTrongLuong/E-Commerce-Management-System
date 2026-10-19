package com.example.ecommerce.order.entity;

import com.example.ecommerce.order.enums.OrderPaymentStatus;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.ticket.entity.PurchaseTicket;
import com.example.ecommerce.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sub_orders")
@ToString(exclude = { "order", "poOwner", "orderItems", "ticket" })
@EqualsAndHashCode(exclude = { "order", "poOwner", "orderItems", "ticket" })
public class SubOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sub_orders_order"))
    private Order order;

    @Column(name = "sub_order_code", length = 50, nullable = false)
    private String subOrderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", foreignKey = @ForeignKey(name = "fk_sub_orders_po"))
    private AppUser poOwner;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "payment_status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderPaymentStatus paymentStatus = OrderPaymentStatus.UNPAID;

    @OneToMany(mappedBy = "subOrder", fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToOne(mappedBy = "subOrder", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PurchaseTicket ticket;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addOrderItem(OrderItem item) {
        orderItems.add(item);
        item.setSubOrder(this);
    }

    public void recalculateTotalAmount() {
        this.totalAmount = orderItems.stream()
                .map(OrderItem::getSubTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
