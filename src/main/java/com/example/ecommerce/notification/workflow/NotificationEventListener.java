package com.example.ecommerce.notification.workflow;

import com.example.ecommerce.ticket.workflow.TicketStatusChangedEvent;

import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.notification.entity.Notification;
import com.example.ecommerce.ticket.entity.PurchaseTicket;
import com.example.ecommerce.ticket.enums.TicketStatus;
import com.example.ecommerce.notification.repository.NotificationRepository;
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
                message = "Bạn có một yêu cầu phê duyệt đơn hàng mới chờ xử lý: Mã phiếu #" + ticket.getId();
            }
            case APPROVED -> {
                recipient = ticket.getRequester();
                message = "Yêu cầu phê duyệt đơn hàng #" + ticket.getId() + " của bạn đã được duyệt thành công!";
            }
            case REJECTED -> {
                recipient = ticket.getRequester();
                message = "Yêu cầu phê duyệt đơn hàng #" + ticket.getId() + " của bạn đã bị từ chối.";
            }
            case RETURNED -> {
                recipient = ticket.getRequester();
                message = "Yêu cầu phê duyệt đơn hàng #" + ticket.getId() + " của bạn bị yêu cầu chỉnh sửa lại.";
            }
            case CANCELLED -> {
                recipient = ticket.getApprover();
                message = "Yêu cầu phê duyệt đơn hàng #" + ticket.getId() + " đã bị hủy.";
            }
            case EXPIRED -> {
                recipient = ticket.getRequester();
                message = "Yêu cầu phê duyệt đơn hàng #" + ticket.getId() + " của bạn đã hết hạn xử lý (quá SLA).";
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
