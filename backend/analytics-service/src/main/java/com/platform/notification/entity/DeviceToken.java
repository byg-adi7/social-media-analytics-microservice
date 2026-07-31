package com.platform.notification.entity;

import com.platform.notification.constant.DevicePlatform;
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
 * A registered Firebase Cloud Messaging token for one physical device
 * install. token is globally unique (not per-user) so re-registering the
 * same token - app reinstall, a different user signing into the same
 * physical device, Firebase's token-refresh callback firing again - always
 * upserts a single row rather than accumulating duplicates or letting two
 * users simultaneously "own" one device's token.
 */
@Entity
@Table(
        name = "device_tokens",
        indexes = @Index(name = "idx_device_tokens_user_id", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, unique = true, length = 4096)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private DevicePlatform platform;

    /**
     * Set false when FCM reports the token as unregistered/invalid (app
     * uninstalled, token revoked) or the user explicitly logs out - kept
     * around rather than deleted so a later re-registration of the exact
     * same token string still upserts cleanly against the unique constraint.
     */
    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;
}
