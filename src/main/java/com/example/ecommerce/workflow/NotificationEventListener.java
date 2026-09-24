package com.example.ecommerce.workflow;

import com.example.ecommerce.entity.AppUser;
import com.example.ecommerce.entity.Notification;
import com.example.ecommerce.entity.PurchaseTicket;
import com.example.ecommerce.enums.TicketStatus;
import com.example.ecommerce.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTicketStatusChanged(TicketStatusChangedEvent event) {
        PurchaseTicket ticket = event.getTicket();
        TicketStatus toStatus = event.getToStatus();

        log.info("NotificationEventListener triggered for Ticket {} status {}", ticket.getId(), toStatus);

        AppUser recipient = null;
        String message = "";

        switch (toStatus) {
            case PENDING_APPROVAL -> {
                recipient = ticket.getApprover();
                message = "You have a new purchase ticket pending approval: Ticket #" + ticket.getId();
            }
            case APPROVED -> {
                recipient = ticket.getRequester();
                message = "Your purchase ticket #" + ticket.getId() + " has been approved!";
            }
            case REJECTED -> {
                recipient = ticket.getRequester();
                message = "Your purchase ticket #" + ticket.getId() + " was rejected.";
            }
            case RETURNED -> {
                recipient = ticket.getRequester();
                message = "Your purchase ticket #" + ticket.getId() + " was returned for modification.";
            }
            case CANCELLED -> {
                recipient = ticket.getApprover();
                message = "Purchase ticket #" + ticket.getId() + " was cancelled.";
            }
            case EXPIRED -> {
                recipient = ticket.getRequester();
                message = "Your purchase ticket #" + ticket.getId() + " has expired.";
            }
        }

        if (recipient != null) {
            Notification notification = Notification.builder()
                    .user(recipient)
                    .type("TICKET_STATUS_CHANGED")
                    .ticket(ticket)
                    .message(message)
                    .build();
            notificationRepository.save(notification);
        }
    }
}
