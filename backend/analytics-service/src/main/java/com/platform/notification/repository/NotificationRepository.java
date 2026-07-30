package com.platform.notification.repository;

import com.platform.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    // Sort order comes from the Pageable itself (see NotificationController's
    // @PageableDefault) rather than an OrderBy clause here, so there's one
    // source of truth instead of two competing sort specifications.
    Page<Notification> findAllByUserId(UUID userId, Pageable pageable);
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
}
