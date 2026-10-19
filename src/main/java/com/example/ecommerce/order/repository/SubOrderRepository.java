package com.example.ecommerce.order.repository;

import com.example.ecommerce.order.entity.SubOrder;
import com.example.ecommerce.order.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubOrderRepository extends JpaRepository<SubOrder, Long> {
    List<SubOrder> findByOrderId(Integer orderId);

    Optional<SubOrder> findByOrderIdAndId(Integer orderId, Long id);

    List<SubOrder> findByPoOwnerId(Long poId);

    List<SubOrder> findByOrderIdAndStatus(Integer orderId, OrderStatus status);

    @Query("SELECT COALESCE(SUM(so.totalAmount), 0) FROM SubOrder so WHERE so.poOwner.id = :poId")
    BigDecimal sumTotalAmountByPoOwnerId(@Param("poId") Long poId);

    @Query("SELECT COUNT(so) FROM SubOrder so WHERE so.poOwner.id = :poId")
    Long countByPoOwnerId(@Param("poId") Long poId);

    @Query("SELECT COALESCE(SUM(so.totalAmount), 0) FROM SubOrder so WHERE so.poOwner.id = :poId AND so.status = :status")
    BigDecimal sumTotalAmountByPoOwnerIdAndStatus(@Param("poId") Long poId, @Param("status") OrderStatus status);

    @Query("SELECT COUNT(so) FROM SubOrder so WHERE so.poOwner.id = :poId AND so.status = :status")
    Long countByPoOwnerIdAndStatus(@Param("poId") Long poId, @Param("status") OrderStatus status);

    @Query("SELECT COALESCE(SUM(so.totalAmount), 0) FROM SubOrder so " +
           "WHERE so.poOwner.id = :poId " +
           "AND so.status IN :statuses " +
           "AND (" +
           "  (so.order.paidAt IS NOT NULL AND so.order.paidAt BETWEEN :startOfDay AND :endOfDay) " +
           "  OR (so.order.paidAt IS NULL AND (so.createdAt BETWEEN :startOfDay AND :endOfDay OR so.updatedAt BETWEEN :startOfDay AND :endOfDay))" +
           ")")
    BigDecimal sumRevenueByPoOwnerIdAndDate(
            @Param("poId") Long poId,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COUNT(so) FROM SubOrder so " +
           "WHERE so.poOwner.id = :poId " +
           "AND so.status IN :statuses " +
           "AND (" +
           "  (so.order.paidAt IS NOT NULL AND so.order.paidAt BETWEEN :startOfDay AND :endOfDay) " +
           "  OR (so.order.paidAt IS NULL AND (so.createdAt BETWEEN :startOfDay AND :endOfDay OR so.updatedAt BETWEEN :startOfDay AND :endOfDay))" +
           ")")
    Long countRevenueByPoOwnerIdAndDate(
            @Param("poId") Long poId,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);
}

