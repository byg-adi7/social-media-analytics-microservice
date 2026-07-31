package com.platform.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per user, created lazily on first read/write (see
 * NotificationPreferenceServiceImpl) rather than at signup - a user who
 * never opens notification settings never gets a row, and getForUser
 * falls back to all-enabled defaults.
 */
@Entity
@Table(name = "notification_preferences", uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_preferences_user_id", columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Gates the FCM push channel only - the in-app notification row is always created regardless. */
    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    /**
     * Reserved for a future email-notification channel, which does not
     * exist yet in this codebase - toggling this currently has no
     * observable effect beyond being echoed back by the preferences API.
     */
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
