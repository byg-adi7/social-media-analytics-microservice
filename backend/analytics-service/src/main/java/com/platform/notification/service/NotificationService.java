package com.platform.notification.service;

import com.platform.analytics.dto.response.PagedResponse;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.dto.response.NotificationResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    NotificationResponse create(UUID userId, NotificationType type, String message);

    PagedResponse<NotificationResponse> getForUser(UUID userId, Pageable pageable);

    NotificationResponse markAsRead(UUID userId, UUID notificationId);
}
