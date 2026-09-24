package com.example.ecommerce.scheduler;

import com.example.ecommerce.entity.PurchaseTicket;
import com.example.ecommerce.enums.TicketAction;
import com.example.ecommerce.enums.TicketStatus;
import com.example.ecommerce.repository.PurchaseTicketRepository;
import com.example.ecommerce.workflow.TicketStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketSlaExecutor {

    private final PurchaseTicketRepository ticketRepository;
    private final TicketStateMachine stateMachine;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireSingleTicket(Long ticketId) {
        ticketRepository.findById(ticketId).ifPresent(ticket -> {
            if (ticket.getStatus() == TicketStatus.PENDING_APPROVAL || ticket.getStatus() == TicketStatus.RETURNED) {
                stateMachine.transition(ticket, TicketAction.EXPIRE, "Ticket expired due to SLA timeout");
                ticketRepository.save(ticket);
                log.info("Ticket {} auto-expired successfully", ticket.getId());
            }
        });
    }
}
