package com.platform.notification.service;

import com.platform.analytics.dto.response.PagedResponse;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.dto.response.NotificationResponse;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface NotificationService {

    /** Persists an in-app notification row and returns it - no push, no preference check. */
    NotificationResponse createNotification(UUID userId, NotificationType type, String title, String message,
                                             Map<String, Object> data);

    /**
     * The entry point every business-event trigger should call: persists the
     * in-app notification (always, synchronously, in the caller's ambient
     * transaction) and, if the user hasn't disabled push, fans a push
     * notification out to their registered devices asynchronously - a slow
     * or failing push send must never delay or fail the caller.
     */
    void notifyUser(UUID userId, NotificationType type, String title, String message, Map<String, Object> data);

    PagedResponse<NotificationResponse> getForUser(UUID userId, Pageable pageable);

    long getUnreadCount(UUID userId);

    NotificationResponse markAsRead(UUID userId, UUID notificationId);

    /** Returns how many notifications were flipped from unread to read. */
    int markAllAsRead(UUID userId);

    /** Permanently deletes every notification belonging to this user. */
    void deleteAllForUser(UUID userId);
}
