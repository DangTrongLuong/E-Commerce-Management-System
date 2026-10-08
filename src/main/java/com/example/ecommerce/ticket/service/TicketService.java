package com.example.ecommerce.ticket.service;

import com.example.ecommerce.common.exception.BadRequestExeption;
import com.example.ecommerce.common.exception.ConflictException;
import com.example.ecommerce.common.exception.InsufficientStockException;
import com.example.ecommerce.common.exception.InvalidTicketStatusException;
import com.example.ecommerce.common.exception.MixedOwnerOrderException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.common.exception.SelfApprovalNotAllowedException;
import com.example.ecommerce.customer.entity.Customer;
import com.example.ecommerce.order.entity.Order;
import com.example.ecommerce.order.entity.OrderItem;
import com.example.ecommerce.order.event.OrderInvoiceApprovedEvent;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.product.enums.ProductStatus;
import com.example.ecommerce.product.repository.ProductRepository;
import com.example.ecommerce.ticket.entity.PurchaseTicket;
import com.example.ecommerce.ticket.entity.TicketHistory;
import com.example.ecommerce.ticket.enums.TicketAction;
import com.example.ecommerce.ticket.enums.TicketStatus;
import com.example.ecommerce.ticket.repository.PurchaseTicketRepository;
import com.example.ecommerce.ticket.repository.TicketHistoryRepository;
import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.user.enums.Role;

import com.example.ecommerce.order.dto.OrderItemRequest;
import com.example.ecommerce.ticket.dto.TicketActionRequest;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.ticket.dto.TicketHistoryResponse;
import com.example.ecommerce.ticket.dto.TicketResponse;
import com.example.ecommerce.common.util.SecurityUtils;
import com.example.ecommerce.ticket.workflow.TicketStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final PurchaseTicketRepository ticketRepository;
    private final TicketHistoryRepository historyRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final TicketStateMachine stateMachine;
    private final SecurityUtils securityUtils;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> getMyTickets(TicketStatus status, int page, int size) {
        AppUser currentUser = securityUtils.getCurrentUser();
        int cappedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(page, cappedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PurchaseTicket> pageResult = status != null
                ? ticketRepository.findByRequesterIdAndStatus(currentUser.getId(), status, pageable)
                : ticketRepository.findByRequesterId(currentUser.getId(), pageable);

        return PageResponse.of(pageResult.map(this::mapToResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> getInboxTickets(TicketStatus status, int page, int size) {
        AppUser currentUser = securityUtils.getCurrentUser();
        int cappedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(page, cappedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PurchaseTicket> pageResult;
        TicketStatus filterStatus = status != null ? status : TicketStatus.PENDING_APPROVAL;

        if (currentUser.getRole() == Role.ADMIN) {
            pageResult = ticketRepository.findByStatus(filterStatus, pageable);
        } else {
            pageResult = ticketRepository.findByApproverIdAndStatus(currentUser.getId(), filterStatus, pageable);
        }

        return PageResponse.of(pageResult.map(this::mapToResponse));
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id) {
        PurchaseTicket ticket = findTicketOrThrow(id);
        verifyTicketVisibility(ticket);
        return mapToResponse(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketHistoryResponse> getTicketHistory(Long id) {
        PurchaseTicket ticket = findTicketOrThrow(id);
        verifyTicketVisibility(ticket);

        return historyRepository.findByTicketIdOrderByCreatedAtDesc(id).stream()
                .map(this::mapToHistoryResponse)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public TicketResponse approveTicket(Long id, TicketActionRequest request) {
        PurchaseTicket ticket = findTicketOrThrow(id);
        AppUser currentUser = securityUtils.getCurrentUser();

        // Separation of duties
        if (ticket.getRequester().getId().equals(currentUser.getId())) {
            throw new SelfApprovalNotAllowedException("Người tạo yêu cầu không được phép tự duyệt phiếu của chính mình");
        }

        verifyApproverAuthority(ticket, currentUser);

        TicketStatus fromStatus = ticket.getStatus();
        String comment = request != null ? request.getComment() : null;

        TicketStatus toStatus = stateMachine.transition(ticket, TicketAction.APPROVE, comment);
        ticketRepository.save(ticket);

        recordHistory(ticket, TicketAction.APPROVE, fromStatus, toStatus, currentUser, comment);

        if (toStatus == TicketStatus.APPROVED) {
            eventPublisher.publishEvent(new OrderInvoiceApprovedEvent(
                    ticket.getOrder().getId(),
                    ticket.getOrder().getCustomer() != null ? ticket.getOrder().getCustomer().getEmail() : null));
        }

        return mapToResponse(ticket);
    }

    @Transactional(rollbackFor = Exception.class)
    public TicketResponse rejectTicket(Long id, TicketActionRequest request) {
        PurchaseTicket ticket = findTicketOrThrow(id);
        AppUser currentUser = securityUtils.getCurrentUser();

        if (request == null || request.getComment() == null || request.getComment().trim().isEmpty()) {
            throw new BadRequestExeption("Vui lòng nhập ghi chú lý do khi từ chối vé phê duyệt");
        }

        verifyApproverAuthority(ticket, currentUser);

        TicketStatus fromStatus = ticket.getStatus();
        TicketStatus toStatus = stateMachine.transition(ticket, TicketAction.REJECT, request.getComment());
        ticketRepository.save(ticket);

        recordHistory(ticket, TicketAction.REJECT, fromStatus, toStatus, currentUser, request.getComment());

        return mapToResponse(ticket);
    }

    @Transactional(rollbackFor = Exception.class)
    public TicketResponse returnTicket(Long id, TicketActionRequest request) {
        PurchaseTicket ticket = findTicketOrThrow(id);
        AppUser currentUser = securityUtils.getCurrentUser();

        if (request == null || request.getComment() == null || request.getComment().trim().isEmpty()) {
            throw new BadRequestExeption("Vui lòng nhập ghi chú lý do khi yêu cầu chỉnh sửa vé");
        }

        verifyApproverAuthority(ticket, currentUser);

        TicketStatus fromStatus = ticket.getStatus();
        TicketStatus toStatus = stateMachine.transition(ticket, TicketAction.RETURN, request.getComment());
        ticketRepository.save(ticket);

        recordHistory(ticket, TicketAction.RETURN, fromStatus, toStatus, currentUser, request.getComment());

        return mapToResponse(ticket);
    }

    @Transactional(rollbackFor = Exception.class)
    public TicketResponse updateTicketItems(Long id, List<OrderItemRequest> newItemRequests) {
        PurchaseTicket ticket = findTicketOrThrow(id);
        AppUser currentUser = securityUtils.getCurrentUser();

        if (!ticket.getRequester().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Chỉ người tạo yêu cầu mới có quyền chỉnh sửa danh sách sản phẩm của vé");
        }

        if (ticket.getStatus() != TicketStatus.RETURNED) {
            throw new InvalidTicketStatusException("Chỉ có thể cập nhật sản phẩm khi vé ở trạng thái YÊU CẦU SỬA (RETURNED)");
        }

        Order order = ticket.getOrder();

        // 1. Refund existing order items stock
        for (OrderItem item : order.getOrderItems()) {
            Product p = item.getProduct();
            p.setStock(p.getStock() + item.getQuantity());
            productRepository.save(p);
        }

        // 2. Validate and deduct new items stock
        order.getOrderItems().clear();
        Map<Integer, Integer> mergedQuantities = new LinkedHashMap<>();
        for (OrderItemRequest req : newItemRequests) {
            mergedQuantities.merge(req.getProductId(), req.getQuantity(), Integer::sum);
        }

        AppUser expectedOwner = null;
        for (Map.Entry<Integer, Integer> entry : mergedQuantities.entrySet()) {
            Product p = productRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với mã ID: " + entry.getKey()));

            if (expectedOwner == null) {
                expectedOwner = p.getOwner();
            } else if (p.getOwner() != null && !p.getOwner().getId().equals(expectedOwner.getId())) {
                throw new MixedOwnerOrderException("Đơn hàng không được chứa sản phẩm từ nhiều nhà cung cấp khác nhau");
            }

            if (p.getStatus() != ProductStatus.ACTIVE) {
                throw new ConflictException("Sản phẩm '" + p.getName() + "' đang ở trạng thái ngừng kinh doanh");
            }

            if (p.getStock() < entry.getValue()) {
                throw new InsufficientStockException("Sản phẩm '" + p.getName() + "' không đủ số lượng tồn kho");
            }

            p.setStock(p.getStock() - entry.getValue());
            productRepository.save(p);

            OrderItem item = OrderItem.builder()
                    .product(p)
                    .quantity(entry.getValue())
                    .unitPrice(p.getPrice())
                    .build();
            item.caculateSubtotal();
            order.addItems(item);
        }

        order.recaculateTotalAmount();
        orderRepository.save(order);

        recordHistory(ticket, TicketAction.UPDATE, TicketStatus.RETURNED, TicketStatus.RETURNED, currentUser,
                "Updated order items");

        return mapToResponse(ticket);
    }

    @Transactional(rollbackFor = Exception.class)
    public TicketResponse resubmitTicket(Long id, TicketActionRequest request) {
        PurchaseTicket ticket = findTicketOrThrow(id);
        AppUser currentUser = securityUtils.getCurrentUser();

        if (!ticket.getRequester().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Chỉ người tạo yêu cầu mới có quyền gửi lại vé này");
        }

        TicketStatus fromStatus = ticket.getStatus();
        String comment = request != null ? request.getComment() : null;

        TicketStatus toStatus = stateMachine.transition(ticket, TicketAction.RESUBMIT, comment);
        ticketRepository.save(ticket);

        recordHistory(ticket, TicketAction.RESUBMIT, fromStatus, toStatus, currentUser, comment);

        return mapToResponse(ticket);
    }

    @Transactional(rollbackFor = Exception.class)
    public TicketResponse cancelTicket(Long id, TicketActionRequest request) {
        PurchaseTicket ticket = findTicketOrThrow(id);
        AppUser currentUser = securityUtils.getCurrentUser();

        if (!ticket.getRequester().getId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Chỉ người tạo yêu cầu hoặc Quản trị viên mới có quyền hủy vé này");
        }

        TicketStatus fromStatus = ticket.getStatus();
        String comment = request != null ? request.getComment() : null;

        TicketStatus toStatus = stateMachine.transition(ticket, TicketAction.CANCEL, comment);
        ticketRepository.save(ticket);

        recordHistory(ticket, TicketAction.CANCEL, fromStatus, toStatus, currentUser, comment);

        return mapToResponse(ticket);
    }

    private PurchaseTicket findTicketOrThrow(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé phê duyệt với mã ID: " + id));
    }

    private void verifyTicketVisibility(PurchaseTicket ticket) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (ticket.getRequester().getId().equals(currentUser.getId())) {
            return;
        }
        if (ticket.getApprover() != null && ticket.getApprover().getId().equals(currentUser.getId())) {
            return;
        }
        // DD-07: Return 404 for unseen resources
        throw new ResourceNotFoundException("Không tìm thấy vé phê duyệt với mã ID: " + ticket.getId());
    }

    private void verifyApproverAuthority(PurchaseTicket ticket, AppUser currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() == Role.PRODUCT_OWNER && ticket.getApprover() != null
                && ticket.getApprover().getId().equals(currentUser.getId())) {
            return;
        }
        throw new AccessDeniedException("Bạn không có quyền thực thi tác vụ trên vé phê duyệt này");
    }

    private void recordHistory(PurchaseTicket ticket, TicketAction action, TicketStatus from, TicketStatus to,
            AppUser actor, String comment) {
        TicketHistory history = TicketHistory.builder()
                .ticket(ticket)
                .action(action)
                .fromStatus(from)
                .toStatus(to)
                .actor(actor)
                .comment(comment)
                .build();
        ticket.addHistory(history);
        historyRepository.saveAndFlush(history);
    }

    private TicketHistoryResponse mapToHistoryResponse(TicketHistory history) {
        return TicketHistoryResponse.builder()
                .id(history.getId())
                .ticketId(history.getTicket().getId())
                .action(history.getAction())
                .fromStatus(history.getFromStatus())
                .toStatus(history.getToStatus())
                .actorId(history.getActor() != null ? history.getActor().getId() : null)
                .comment(history.getComment())
                .createdAt(history.getCreatedAt())
                .build();
    }

    private TicketResponse mapToResponse(PurchaseTicket ticket) {
        String lastComment = historyRepository
                .findFirstByTicketIdAndCommentIsNotNullAndCommentNotOrderByCreatedAtDesc(ticket.getId(), "")
                .map(TicketHistory::getComment)
                .orElse(null);

        Customer customer = ticket.getOrder().getCustomer();
        TicketResponse.RequesterDto requesterDto = TicketResponse.RequesterDto.builder()
                .id(ticket.getRequester().getId())
                .name(customer != null ? customer.getName() : ticket.getRequester().getEmail())
                .phone(customer != null ? customer.getPhone() : null)
                .build();

        return TicketResponse.builder()
                .id(ticket.getId())
                .orderId(Long.valueOf(ticket.getOrder().getId()))
                .status(ticket.getStatus())
                .requester(requesterDto)
                .approverId(ticket.getApprover() != null ? ticket.getApprover().getId() : null)
                .currentLevel(ticket.getCurrentLevel())
                .requiredLevel(ticket.getRequiredLevel())
                .dueAt(ticket.getDueAt())
                .lastComment(lastComment)
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }
}
