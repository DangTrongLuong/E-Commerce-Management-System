package com.example.ecommerce.ticket.service;

import com.example.ecommerce.ticket.entity.PurchaseTicket;
import com.example.ecommerce.ticket.enums.TicketStatus;
import com.example.ecommerce.ticket.repository.PurchaseTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketSlaScheduler {

    private final PurchaseTicketRepository ticketRepository;
    private final TicketSlaExecutor ticketSlaExecutor;

    @Scheduled(cron = "${app.ticket.sla-cron:0 0 0 * * *}")
    @SchedulerLock(name = "TicketSlaScheduler_processOverdueTickets", lockAtLeastFor = "1m", lockAtMostFor = "10m")
    public void processOverdueTickets() {
        LocalDateTime now = LocalDateTime.now();
        List<PurchaseTicket> overdueTickets = ticketRepository.findByStatusInAndDueAtBefore(
                List.of(TicketStatus.PENDING_APPROVAL, TicketStatus.RETURNED),
                now);

        if (overdueTickets.isEmpty()) {
            return;
        }

        log.info("Found {} overdue tickets for SLA expiration", overdueTickets.size());
        for (PurchaseTicket ticket : overdueTickets) {
            try {
                ticketSlaExecutor.expireSingleTicket(ticket.getId());
            } catch (Exception e) {
                log.error("Failed to expire ticket {}: {}", ticket.getId(), e.getMessage(), e);
            }
        }
    }
}
