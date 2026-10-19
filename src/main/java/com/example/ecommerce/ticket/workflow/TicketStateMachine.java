package com.example.ecommerce.ticket.workflow;

import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.entity.OrderItem;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.ticket.entity.PurchaseTicket;
import com.example.ecommerce.order.entity.SubOrder;
import com.example.ecommerce.order.enums.OrderStatus;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.order.repository.SubOrderRepository;
import com.example.ecommerce.ticket.enums.TicketAction;
import com.example.ecommerce.ticket.enums.TicketStatus;
import com.example.ecommerce.common.exception.InvalidTicketStatusException;
import com.example.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketStateMachine {

    private final ProductRepository productRepository;
    private final SubOrderRepository subOrderRepository;
    private final OrderRepository orderRepository;

    public TicketStatus transition(PurchaseTicket ticket, TicketAction action, String comment) {
        TicketStatus current = ticket.getStatus();
        TicketStatus nextStatus;

        switch (current) {
            case PENDING_PO_APPROVE -> {
                switch (action) {
                    case APPROVE -> {
                        ticket.setCurrentLevel(2);
                        nextStatus = TicketStatus.PENDING_AD_APPROVE;
                    }
                    case REJECT -> {
                        validateComment(comment);
                        nextStatus = TicketStatus.REJECTED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket, "Ticket rejected: " + comment);
                    }
                    case RETURN -> {
                        validateComment(comment);
                        nextStatus = TicketStatus.RETURNED;
                    }
                    case CANCEL -> {
                        nextStatus = TicketStatus.CANCELLED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket, "Ticket cancelled by requester");
                    }
                    case EXPIRE -> {
                        nextStatus = TicketStatus.EXPIRED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket, "Ticket expired (SLA exceeded)");
                    }
                    default -> throw new InvalidTicketStatusException(
                            "Không thể thực hiện hành động " + action + " khi vé phê duyệt đang ở trạng thái " + current);
                }
            }
            case PENDING_AD_APPROVE -> {
                switch (action) {
                    case APPROVE -> {
                        ticket.setCurrentLevel(2);
                        nextStatus = TicketStatus.APPROVED;
                        ticket.setDecidedAt(LocalDateTime.now());
                    }
                    case REJECT -> {
                        validateComment(comment);
                        nextStatus = TicketStatus.REJECTED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket, "Ticket rejected: " + comment);
                    }
                    case RETURN -> {
                        validateComment(comment);
                        nextStatus = TicketStatus.RETURNED;
                    }
                    case CANCEL -> {
                        nextStatus = TicketStatus.CANCELLED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket, "Ticket cancelled by requester");
                    }
                    case EXPIRE -> {
                        nextStatus = TicketStatus.EXPIRED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket, "Ticket expired (SLA exceeded)");
                    }
                    default -> throw new InvalidTicketStatusException(
                            "Không thể thực hiện hành động " + action + " khi vé phê duyệt đang ở trạng thái " + current);
                }
            }
            case PENDING_APPROVAL -> {
                switch (action) {
                    case APPROVE -> {
                        if (ticket.getCurrentLevel() < ticket.getRequiredLevel()) {
                            ticket.setCurrentLevel(ticket.getCurrentLevel() + 1);
                            nextStatus = (ticket.getCurrentLevel() == 2)
                                    ? TicketStatus.PENDING_AD_APPROVE
                                    : TicketStatus.PENDING_APPROVAL;
                        } else {
                            nextStatus = TicketStatus.APPROVED;
                            ticket.setDecidedAt(LocalDateTime.now());
                        }
                    }
                    case REJECT -> {
                        validateComment(comment);
                        nextStatus = TicketStatus.REJECTED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket, "Ticket rejected: " + comment);
                    }
                    case RETURN -> {
                        validateComment(comment);
                        nextStatus = TicketStatus.RETURNED;
                    }
                    case CANCEL -> {
                        nextStatus = TicketStatus.CANCELLED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket, "Ticket cancelled by requester");
                    }
                    case EXPIRE -> {
                        nextStatus = TicketStatus.EXPIRED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket, "Ticket expired (SLA exceeded)");
                    }
                    default -> throw new InvalidTicketStatusException(
                            "Không thể thực hiện hành động " + action + " khi vé phê duyệt đang ở trạng thái " + current);
                }
            }
            case RETURNED -> {
                switch (action) {
                    case UPDATE -> nextStatus = TicketStatus.RETURNED;
                    case RESUBMIT -> {
                        if (ticket.getRequiredLevel() == 2) {
                            nextStatus = TicketStatus.PENDING_PO_APPROVE;
                            ticket.setCurrentLevel(1);
                        } else {
                            nextStatus = TicketStatus.PENDING_APPROVAL;
                            ticket.setCurrentLevel(1);
                        }
                        ticket.setDueAt(LocalDateTime.now().plusHours(48)); // reset SLA
                    }
                    case CANCEL -> {
                        nextStatus = TicketStatus.CANCELLED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket, "Ticket cancelled by requester");
                    }
                    case EXPIRE -> {
                        nextStatus = TicketStatus.EXPIRED;
                        ticket.setDecidedAt(LocalDateTime.now());
                        cancelOrderAndRefundStock(ticket, "Ticket expired (SLA exceeded)");
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

    private void cancelOrderAndRefundStock(PurchaseTicket ticket, String reason) {
        if (ticket == null) return;

        Long subOrderId = ticket.getSubOrder() != null ? ticket.getSubOrder().getId() : null;
        Integer orderId = ticket.getOrder() != null ? ticket.getOrder().getId() : null;

        if (subOrderId != null) {
            subOrderRepository.findById(subOrderId).ifPresent(subOrder -> {
                if (subOrder.getStatus() != OrderStatus.CANCELLED) {
                    subOrder.setStatus(OrderStatus.CANCELLED);
                    List<int[]> refunds = new ArrayList<>();
                    if (subOrder.getOrderItems() != null) {
                        for (OrderItem item : subOrder.getOrderItems()) {
                            refunds.add(new int[]{item.getProduct().getId(), item.getQuantity()});
                        }
                    }
                    subOrderRepository.save(subOrder);

                    for (int[] refund : refunds) {
                        productRepository.increaseStockAtomic(refund[0], refund[1]);
                    }
                }
            });

            if (orderId != null) {
                List<SubOrder> allSubOrders = subOrderRepository.findByOrderId(orderId);
                boolean allCancelled = !allSubOrders.isEmpty() && allSubOrders.stream()
                        .allMatch(s -> s.getStatus() == OrderStatus.CANCELLED);
                if (allCancelled) {
                    orderRepository.findById(orderId).ifPresent(order -> {
                        if (order.getStatus() != OrderStatus.CANCELLED) {
                            order.setStatus(OrderStatus.CANCELLED);
                            order.setCancelReason(reason);
                            orderRepository.save(order);
                        }
                    });
                }
            }
        } else if (orderId != null) {
            orderRepository.findById(orderId).ifPresent(order -> {
                if (order.getStatus() != OrderStatus.CANCELLED) {
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setCancelReason(reason);
                    List<int[]> refunds = new ArrayList<>();
                    if (order.getOrderItems() != null) {
                        for (OrderItem item : order.getOrderItems()) {
                            refunds.add(new int[]{item.getProduct().getId(), item.getQuantity()});
                        }
                    }
                    orderRepository.save(order);

                    for (int[] refund : refunds) {
                        productRepository.increaseStockAtomic(refund[0], refund[1]);
                    }
                }
            });
        }
    }
}
