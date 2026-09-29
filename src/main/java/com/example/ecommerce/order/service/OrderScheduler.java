package com.example.ecommerce.order.service;

import com.example.ecommerce.ticket.service.TicketSlaScheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderScheduler {
    private final TicketSlaScheduler ticketSlaScheduler;

    public void autoCancelExpiredPendingOrders() {
        log.info("[OrderScheduler] Executing ticket SLA expiration job...");
        try {
            ticketSlaScheduler.processOverdueTickets();
        } catch (Exception e) {
            log.error("[OrderScheduler] Error executing ticket SLA expiration job", e);
        }
    }
}
