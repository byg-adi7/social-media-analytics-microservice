package com.platform.notification.repository;

import com.platform.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    // Sort order comes from the Pageable itself (see NotificationController's
    // @PageableDefault) rather than an OrderBy clause here, so there's one
    // source of truth instead of two competing sort specifications.
    Page<Notification> findAllByUserId(UUID userId, Pageable pageable);
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.userId = :userId AND n.read = false")
    int markAllAsReadForUser(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);

    void deleteAllByUserId(UUID userId);
}
