package com.example.ecommerce.notification.service;

import com.example.ecommerce.notification.entity.EmailOutbox;
import com.example.ecommerce.notification.enums.EmailOutboxStatus;
import com.example.ecommerce.notification.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailOutboxWorker {

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxService emailOutboxService;

    /**
     * Worker chạy mỗi 10 giây.
     * Mỗi lần chạy, worker lấy tối đa 50 dòng PENDING có NEXT_ATTEMPT_AT <= thời điểm hiện tại, theo ID tăng dần.
     */
    @Scheduled(fixedDelay = 10000)
    public void processPendingEmails() {
        LocalDateTime now = LocalDateTime.now();
        List<EmailOutbox> pendingList = emailOutboxRepository
                .findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                        EmailOutboxStatus.PENDING,
                        now,
                        PageRequest.of(0, 50)
                );

        if (pendingList.isEmpty()) {
            return;
        }

        log.info("[EmailOutboxWorker] Tìm thấy {} email PENDING cần gửi", pendingList.size());
        for (EmailOutbox outbox : pendingList) {
            try {
                emailOutboxService.processOutboxItem(outbox);
            } catch (Exception e) {
                log.error("[EmailOutboxWorker] Lỗi ngoài dự kiến khi xử lý outbox #{}: {}", outbox.getId(), e.getMessage());
            }
        }
    }
}
