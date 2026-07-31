package com.platform.notification.service;

import com.platform.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.platform.notification.dto.response.NotificationPreferenceResponse;

import java.util.UUID;

public interface NotificationPreferenceService {

    NotificationPreferenceResponse getForUser(UUID userId);

    NotificationPreferenceResponse update(UUID userId, UpdateNotificationPreferenceRequest request);

    /** Whether push notifications should be sent for this user - defaults to true if no preference row exists yet. */
    boolean isPushEnabled(UUID userId);

    void deleteAllForUser(UUID userId);
}
