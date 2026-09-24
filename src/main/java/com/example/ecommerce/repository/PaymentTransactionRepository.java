package com.example.ecommerce.repository;

import com.example.ecommerce.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByVnpTxnRef(String vnpTxnRef);

    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(Integer orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentTransaction p WHERE p.vnpTxnRef = :vnpTxnRef")
    Optional<PaymentTransaction> findByVnpTxnRefForUpdate(String vnpTxnRef);
}
