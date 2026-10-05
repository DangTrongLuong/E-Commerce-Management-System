package com.example.ecommerce.ticket.repository;

import com.example.ecommerce.ticket.entity.TicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {

    List<TicketHistory> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    Optional<TicketHistory> findFirstByTicketIdAndCommentIsNotNullAndCommentNotOrderByCreatedAtDesc(Long ticketId,
            String emptyComment);

}
