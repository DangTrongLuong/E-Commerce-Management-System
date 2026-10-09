package com.example.ecommerce.ticket.repository;

import com.example.ecommerce.ticket.entity.PurchaseTicket;
import com.example.ecommerce.ticket.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseTicketRepository extends JpaRepository<PurchaseTicket, Long> {

    @EntityGraph(attributePaths = { "order", "order.orderItems", "order.customer", "requester", "approver" })
    Optional<PurchaseTicket> findById(Long id);

    @EntityGraph(attributePaths = { "order", "order.customer", "requester", "approver" })
    Optional<PurchaseTicket> findByOrderId(Long orderId);

    @EntityGraph(attributePaths = { "order", "order.customer", "requester", "approver" })
    Page<PurchaseTicket> findByRequesterId(Long requesterId, Pageable pageable);

    @EntityGraph(attributePaths = { "order", "order.customer", "requester", "approver" })
    Page<PurchaseTicket> findByRequesterIdAndStatus(Long requesterId, TicketStatus status, Pageable pageable);

    @EntityGraph(attributePaths = { "order", "order.customer", "requester", "approver" })
    Page<PurchaseTicket> findByApproverId(Long approverId, Pageable pageable);

    @EntityGraph(attributePaths = { "order", "order.customer", "requester", "approver" })
    Page<PurchaseTicket> findByApproverIdAndStatus(Long approverId, TicketStatus status, Pageable pageable);

    @EntityGraph(attributePaths = { "order", "order.customer", "requester", "approver" })
    Page<PurchaseTicket> findByStatus(TicketStatus status, Pageable pageable);

    List<PurchaseTicket> findByStatusInAndDueAtBefore(Collection<TicketStatus> statuses, LocalDateTime dueAt);
}
