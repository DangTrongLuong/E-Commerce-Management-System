package com.example.ecommerce.payment.entity;

import com.example.ecommerce.payment.enums.PaymentMethod;
import com.example.ecommerce.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_transaction",
        indexes = {
                @Index(name = "idx_payment_order_id", columnList = "order_id"),
                @Index(name = "idx_payment_status", columnList = "status")
        }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentTransaction {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "order_id", nullable = false)
        private Integer orderId;

        @Column(name = "vnp_txn_ref", nullable = false, unique = true, length = 100)
        private String vnpTxnRef;

        @Column(name = "vnp_transaction_no", length = 50)
        private String vnpTransactionNo;

        @Column(name = "amount", nullable = false, precision = 15, scale = 2)
        private BigDecimal amount;

        @Column(name = "bank_code", length = 20)
        private String bankCode;

        @Enumerated(EnumType.STRING)
        @Column(name = "payment_method", nullable = false, length = 20)
        @Builder.Default
        private PaymentMethod paymentMethod = PaymentMethod.VNPAY;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 20)
        @Builder.Default
        private PaymentStatus status = PaymentStatus.PENDING;

        @Column(name = "response_code", length = 10)
        private String responseCode;

        @Column(name = "secure_hash", length = 512)
        private String secureHash;

        @Column(name = "pay_date", length = 20)
        private String payDate;

        @Column(name = "ip_address", length = 45)
        private String ipAddress;

        @Column(name = "order_info", length = 255)
        private String orderInfo;

        @Column(name = "is_ipn_confirmed", nullable = false)
        @Builder.Default
        private boolean ipnConfirmed = false;

        @CreationTimestamp
        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;

        @UpdateTimestamp
        @Column(name = "updated_at", nullable = false)
        private LocalDateTime updatedAt;
}
