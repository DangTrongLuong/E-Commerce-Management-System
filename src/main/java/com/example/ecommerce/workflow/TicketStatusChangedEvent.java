package com.example.ecommerce.workflow;

import com.example.ecommerce.entity.PurchaseTicket;
import com.example.ecommerce.enums.TicketAction;
import com.example.ecommerce.enums.TicketStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TicketStatusChangedEvent extends ApplicationEvent {

    private final PurchaseTicket ticket;
    private final TicketAction action;
    private final TicketStatus fromStatus;
    private final TicketStatus toStatus;

    public TicketStatusChangedEvent(Object source, PurchaseTicket ticket, TicketAction action, TicketStatus fromStatus, TicketStatus toStatus) {
        super(source);
        this.ticket = ticket;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }
}
