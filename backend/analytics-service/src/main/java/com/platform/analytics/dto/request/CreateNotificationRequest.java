package com.platform.analytics.dto.request;

import com.platform.analytics.constant.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Mirrors the Notification Service's internal
 * CreateNotificationRequest shape - the body for POST /internal/notifications.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationRequest {
    private UUID userId;
    private NotificationType type;
    private String message;
}
