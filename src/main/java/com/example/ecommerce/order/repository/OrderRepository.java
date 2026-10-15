package com.example.ecommerce.order.repository;

import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer>, JpaSpecificationExecutor<Order> {
    @Override
    @EntityGraph(attributePaths = { "customer", "orderItems", "orderItems.product" })
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "customer", "orderItems", "orderItems.product" })
    Optional<Order> findById(Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Integer id);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime createdAtBefore);

    List<Order> findByStatusAndUpdatedAtBefore(OrderStatus status, LocalDateTime updatedAtBefore);

    @Query(value = "SELECT COALESCE(SUM(o.total_amount), 0) FROM orders o " +
            "WHERE DATE(DATE_ADD(o.paid_at, INTERVAL 7 HOUR)) = :date " +
            "AND o.status IN ('PAID', 'CONFIRMED', 'COMPLETED')", nativeQuery = true)
    BigDecimal sumRevenueByDate(@Param("date") LocalDate date);

    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countOrdersByStatus();

    @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.customer.id = :customerId GROUP BY o.status")
    List<Object[]> countOrdersByStatusForCustomer(@Param("customerId") Integer customerId);

    @Query(value = "SELECT p.id AS productId, p.name AS name, CAST(COALESCE(SUM(oi.quantity), 0) AS SIGNED) AS quantitySold " +
            "FROM order_items oi " +
            "JOIN orders o ON oi.order_id = o.id " +
            "JOIN products p ON oi.product_id = p.id " +
            "WHERE DATE(DATE_ADD(o.paid_at, INTERVAL 7 HOUR)) BETWEEN :startDate AND :endDate " +
            "AND o.status IN ('PAID', 'CONFIRMED', 'COMPLETED') " +
            "GROUP BY p.id, p.name " +
            "ORDER BY quantitySold DESC, p.id ASC " +
            "LIMIT 5", nativeQuery = true)
    List<TopProductProjection> findTop5Products(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
