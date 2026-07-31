package com.platform.notification.entity;

import com.platform.notification.constant.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An in-app notification for a user. userId is a plain UUID column, not a
 * foreign key - the Auth Service owns the users table, and a real FK
 * constraint across service-owned tables would couple these services'
 * schemas even though they happen to share one physical Postgres instance
 * in this deployment.
 */
@Entity
@Table(
        name = "notifications",
        indexes = @Index(name = "idx_notification_user_id", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    /** Short heading for display (e.g. push notification title / list-row bold text). */
    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    /**
     * Optional JSON-encoded payload (e.g. {"accountId": "..."}) letting the
     * frontend deep-link from a notification without a second API call.
     * Stored as plain text, not a native jsonb column, to avoid a
     * Postgres-specific Hibernate type dependency for what is, so far, a
     * small opaque blob the frontend alone interprets.
     */
    @Column(name = "data", columnDefinition = "TEXT")
    private String data;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
