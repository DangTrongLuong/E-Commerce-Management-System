package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.OrderItemRequest;
import com.example.ecommerce.dto.request.TicketActionRequest;
import com.example.ecommerce.dto.response.ApiResponse;
import com.example.ecommerce.dto.response.PageResponse;
import com.example.ecommerce.dto.response.TicketHistoryResponse;
import com.example.ecommerce.dto.response.TicketResponse;
import com.example.ecommerce.enums.TicketStatus;
import com.example.ecommerce.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "Purchase Ticket Approval Workflow Endpoints")
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get current user's purchase tickets (USER)")
    public ResponseEntity<ApiResponse<PageResponse<TicketResponse>>> getMyTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Fetched my tickets successfully", ticketService.getMyTickets(status, page, size)));
    }

    @GetMapping("/inbox")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Get approval inbox tickets (PO / Admin)")
    public ResponseEntity<ApiResponse<PageResponse<TicketResponse>>> getInboxTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Fetched inbox tickets successfully", ticketService.getInboxTickets(status, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get ticket details by ID")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicketById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success("Fetched ticket details successfully", ticketService.getTicketById(id)));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get ticket audit history")
    public ResponseEntity<ApiResponse<List<TicketHistoryResponse>>> getTicketHistory(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success("Fetched ticket history successfully", ticketService.getTicketHistory(id)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Approve purchase ticket (PO / Admin)")
    public ResponseEntity<ApiResponse<TicketResponse>> approveTicket(
            @PathVariable("id") Long id,
            @RequestBody(required = false) TicketActionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Ticket approved successfully", ticketService.approveTicket(id, request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Reject purchase ticket (PO / Admin - comment required)")
    public ResponseEntity<ApiResponse<TicketResponse>> rejectTicket(
            @PathVariable("id") Long id,
            @Valid @RequestBody TicketActionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Ticket rejected successfully", ticketService.rejectTicket(id, request)));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('PRODUCT_OWNER', 'ADMIN')")
    @Operation(summary = "Return purchase ticket to user for changes (PO / Admin - comment required)")
    public ResponseEntity<ApiResponse<TicketResponse>> returnTicket(
            @PathVariable("id") Long id,
            @Valid @RequestBody TicketActionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Ticket returned successfully", ticketService.returnTicket(id, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Update ticket items when returned (USER)")
    public ResponseEntity<ApiResponse<TicketResponse>> updateTicketItems(
            @PathVariable("id") Long id,
            @Valid @RequestBody List<OrderItemRequest> items
    ) {
        return ResponseEntity.ok(ApiResponse.success("Ticket items updated successfully", ticketService.updateTicketItems(id, items)));
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Resubmit ticket after changes (USER)")
    public ResponseEntity<ApiResponse<TicketResponse>> resubmitTicket(
            @PathVariable("id") Long id,
            @RequestBody(required = false) TicketActionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Ticket resubmitted successfully", ticketService.resubmitTicket(id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Cancel purchase ticket (USER / Admin)")
    public ResponseEntity<ApiResponse<TicketResponse>> cancelTicket(
            @PathVariable("id") Long id,
            @RequestBody(required = false) TicketActionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Ticket cancelled successfully", ticketService.cancelTicket(id, request)));
    }
}
