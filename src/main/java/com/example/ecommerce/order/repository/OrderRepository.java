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
            "AND o.status IN ('PAID', 'CONFIRMED', 'COMPLETED') " +
            "AND (o.parent_id IS NOT NULL OR NOT EXISTS (SELECT 1 FROM orders c WHERE c.parent_id = o.id))", nativeQuery = true)
    BigDecimal sumRevenueByDate(@Param("date") LocalDate date);

    @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.parentOrder IS NULL GROUP BY o.status")
    List<Object[]> countOrdersByStatus();

    @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.customer.id = :customerId AND o.parentOrder IS NULL GROUP BY o.status")
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

    // ADMIN: Tổng tiền và số lượng đơn hàng toàn hệ thống (không tính đơn con để tránh trùng lặp)
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.parentOrder IS NULL")
    BigDecimal sumTotalAmountAll();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.parentOrder IS NULL")
    Long countAllOrders();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.parentOrder IS NULL AND o.status = :status")
    BigDecimal sumTotalAmountByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.parentOrder IS NULL AND o.status = :status")
    Long countByStatus(@Param("status") OrderStatus status);

    // PRODUCT_OWNER (Fallback từ order_items): Tổng tiền và số lượng đơn hàng theo PO
    @Query("SELECT COALESCE(SUM(oi.subTotal), 0) FROM OrderItem oi WHERE oi.product.owner.id = :poId")
    BigDecimal sumSubtotalByProductOwnerId(@Param("poId") Long poId);

    @Query("SELECT COUNT(DISTINCT oi.order.id) FROM OrderItem oi WHERE oi.product.owner.id = :poId")
    Long countOrdersByProductOwnerId(@Param("poId") Long poId);

    @Query("SELECT COALESCE(SUM(oi.subTotal), 0) FROM OrderItem oi WHERE oi.product.owner.id = :poId AND oi.order.status = :status")
    BigDecimal sumSubtotalByProductOwnerIdAndStatus(@Param("poId") Long poId, @Param("status") OrderStatus status);

    @Query("SELECT COUNT(DISTINCT oi.order.id) FROM OrderItem oi WHERE oi.product.owner.id = :poId AND oi.order.status = :status")
    Long countOrdersByProductOwnerIdAndStatus(@Param("poId") Long poId, @Param("status") OrderStatus status);

    // ADMIN: Doanh thu theo ngày (mặc định hôm nay) và danh sách trạng thái
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o " +
           "WHERE o.parentOrder IS NULL " +
           "AND o.status IN :statuses " +
           "AND (" +
           "  (o.paidAt IS NOT NULL AND o.paidAt BETWEEN :startOfDay AND :endOfDay) " +
           "  OR (o.paidAt IS NULL AND (o.createdAt BETWEEN :startOfDay AND :endOfDay OR o.updatedAt BETWEEN :startOfDay AND :endOfDay))" +
           ")")
    BigDecimal sumRevenueByStatusesAndDate(
            @Param("statuses") List<OrderStatus> statuses,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COUNT(o) FROM Order o " +
           "WHERE o.parentOrder IS NULL " +
           "AND o.status IN :statuses " +
           "AND (" +
           "  (o.paidAt IS NOT NULL AND o.paidAt BETWEEN :startOfDay AND :endOfDay) " +
           "  OR (o.paidAt IS NULL AND (o.createdAt BETWEEN :startOfDay AND :endOfDay OR o.updatedAt BETWEEN :startOfDay AND :endOfDay))" +
           ")")
    Long countRevenueByStatusesAndDate(
            @Param("statuses") List<OrderStatus> statuses,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    // PRODUCT_OWNER (Fallback từ order_items): Doanh thu theo PO và ngày
    @Query("SELECT COALESCE(SUM(oi.subTotal), 0) FROM OrderItem oi " +
           "WHERE oi.product.owner.id = :poId " +
           "AND oi.order.status IN :statuses " +
           "AND (" +
           "  (oi.order.paidAt IS NOT NULL AND oi.order.paidAt BETWEEN :startOfDay AND :endOfDay) " +
           "  OR (oi.order.paidAt IS NULL AND (oi.order.createdAt BETWEEN :startOfDay AND :endOfDay OR oi.order.updatedAt BETWEEN :startOfDay AND :endOfDay))" +
           ")")
    BigDecimal sumRevenueByProductOwnerIdAndDate(
            @Param("poId") Long poId,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COUNT(DISTINCT oi.order.id) FROM OrderItem oi " +
           "WHERE oi.product.owner.id = :poId " +
           "AND oi.order.status IN :statuses " +
           "AND (" +
           "  (oi.order.paidAt IS NOT NULL AND oi.order.paidAt BETWEEN :startOfDay AND :endOfDay) " +
           "  OR (oi.order.paidAt IS NULL AND (oi.order.createdAt BETWEEN :startOfDay AND :endOfDay OR oi.order.updatedAt BETWEEN :startOfDay AND :endOfDay))" +
           ")")
    Long countRevenueOrdersByProductOwnerIdAndDate(
            @Param("poId") Long poId,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);
}
