package com.example.ecommerce.service;

import com.example.ecommerce.dto.response.NotificationResponse;
import com.example.ecommerce.dto.response.PageResponse;
import com.example.ecommerce.entity.AppUser;
import com.example.ecommerce.entity.Notification;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.NotificationRepository;
import com.example.ecommerce.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(int page, int size) {
        AppUser currentUser = securityUtils.getCurrentUser();
        int cappedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(page, cappedSize);

        Page<Notification> pageResult = notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable);
        return PageResponse.of(pageResult.map(this::mapToResponse));
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        AppUser currentUser = securityUtils.getCurrentUser();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if (!notification.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Notification not found: " + notificationId);
        }

        notification.setReadAt(LocalDateTime.now());
        Notification saved = notificationRepository.save(notification);
        return mapToResponse(saved);
    }

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUser().getId())
                .type(n.getType())
                .ticketId(n.getTicket() != null ? n.getTicket().getId() : null)
                .message(n.getMessage())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
