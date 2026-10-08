package com.example.ecommerce.order.entity;

import com.example.ecommerce.product.entity.Product;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "order_items")
@ToString(exclude = { "order", "product" })
@EqualsAndHashCode(exclude = { "order", "product" })
public class OrderItem {
    // id, order_id, product_id, quantity, unit_price, subtotal
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int Id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_order"))
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_product"))
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subTotal;

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.caculateSubtotal();
    }

    public void caculateSubtotal() {
        this.subTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

}
