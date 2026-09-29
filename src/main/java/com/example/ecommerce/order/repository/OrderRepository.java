package com.example.ecommerce.order.repository;

import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer>, JpaSpecificationExecutor<Order> {
    @Override
    @EntityGraph(attributePaths = "customer")
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"customer", "orderItems", "orderItems.product"})
    Optional<Order> findById(Integer id);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime createdAtBefore);
}
