package com.example.ecommerce.ticket.workflow;

import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.entity.OrderItem;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.ticket.entity.PurchaseTicket;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.ticket.enums.TicketAction;
import com.example.ecommerce.ticket.enums.TicketStatus;
import com.example.ecommerce.common.exception.InvalidTicketStatusException;
import com.example.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketStateMachine {

    private final ProductRepository productRepository;

    public TicketStatus transition(PurchaseTicket ticket, TicketAction action, String comment) {
        TicketStatus current = ticket.getStatus();
        TicketStatus nextStatus;

        switch (current) {
            case PENDING_APPROVAL -> {
                switch (action) {
                    case APPROVE -> {
                        if (ticket.getCurrentLevel() < ticket.getRequiredLevel()) {
                            ticket.setCurrentLevel(ticket.getCurrentLevel() + 1);
                            nextStatus = TicketStatus.PENDING_APPROVAL;
                        } else {
                            nextStatus = TicketStatus.APPROVED;
                            ticket.setDecidedAt(LocalDateTime.now());
                            ticket.getOrder().setStatus(OrderStatus.CONFIRMED);
                        }
                    }
                    case REJECT -> {
                        validateComment(comment);
                        nextStatus = TicketStatus.REJECTED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket.getOrder(), "Ticket rejected: " + comment);
                    }
                    case RETURN -> {
                        validateComment(comment);
                        nextStatus = TicketStatus.RETURNED;
                    }
                    case CANCEL -> {
                        nextStatus = TicketStatus.CANCELLED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket.getOrder(), "Ticket cancelled by requester");
                    }
                    case EXPIRE -> {
                        nextStatus = TicketStatus.EXPIRED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket.getOrder(), "Ticket expired (SLA exceeded)");
                    }
                    default -> throw new InvalidTicketStatusException(
                            "Không thể thực hiện hành động " + action + " khi vé phê duyệt đang ở trạng thái " + current);
                }
            }
            case RETURNED -> {
                switch (action) {
                    case UPDATE -> nextStatus = TicketStatus.RETURNED;
                    case RESUBMIT -> {
                        nextStatus = TicketStatus.PENDING_APPROVAL;
                        ticket.setDueAt(LocalDateTime.now().plusHours(48)); // reset SLA
                    }
                    case CANCEL -> {
                        nextStatus = TicketStatus.CANCELLED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket.getOrder(), "Ticket cancelled by requester");
                    }
                    case EXPIRE -> {
                        nextStatus = TicketStatus.EXPIRED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket.getOrder(), "Ticket expired (SLA exceeded)");
                    }
                    default -> throw new InvalidTicketStatusException(
                            "Không thể thực hiện hành động " + action + " khi vé phê duyệt đang ở trạng thái " + current);
                }
            }
            case APPROVED, REJECTED, CANCELLED, EXPIRED -> throw new InvalidTicketStatusException(
                    "Vé phê duyệt đang ở trạng thái kết thúc (" + current + ") và không thể thay đổi");
            default -> throw new InvalidTicketStatusException("Trạng thái vé phê duyệt không hợp lệ: " + current);
        }

        ticket.setStatus(nextStatus);
        return nextStatus;
    }

    private void validateComment(String comment) {
        if (comment == null || comment.trim().length() < 10 || comment.trim().length() > 500) {
            throw new IllegalArgumentException("Yêu cầu nhập ghi chú khi TỪ CHỐI hoặc YÊU CẦU SỬA (độ dài từ 10 đến 500 ký tự)");
        }
    }

    private void cancelOrderAndRefundStock(Order order, String reason) {
        if (order.getStatus() != OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReason(reason);
            for (OrderItem item : order.getOrderItems()) {
                productRepository.increaseStockAtomic(item.getProduct().getId(), item.getQuantity());
            }
        }
    }
}
