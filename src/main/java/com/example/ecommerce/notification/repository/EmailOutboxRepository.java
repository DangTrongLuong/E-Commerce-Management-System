package com.example.ecommerce.notification.repository;

import com.example.ecommerce.notification.entity.EmailOutbox;
import com.example.ecommerce.notification.enums.EmailOutboxStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    Optional<EmailOutbox> findByTemplateAndReferenceId(String template, Long referenceId);

    List<EmailOutbox> findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            EmailOutboxStatus status,
            LocalDateTime nextAttemptAt,
            Pageable pageable
    );

    Page<EmailOutbox> findByStatus(EmailOutboxStatus status, Pageable pageable);
}
