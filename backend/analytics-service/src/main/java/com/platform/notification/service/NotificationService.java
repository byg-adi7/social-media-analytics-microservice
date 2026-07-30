package com.platform.notification.service;

import com.platform.notification.constant.NotificationType;
import com.platform.notification.dto.response.NotificationResponse;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    NotificationResponse create(UUID userId, NotificationType type, String message);

    List<NotificationResponse> getForUser(UUID userId);

    NotificationResponse markAsRead(UUID userId, UUID notificationId);
}
