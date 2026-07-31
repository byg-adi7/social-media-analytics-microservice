package com.platform.notification.service;

import java.util.Map;
import java.util.UUID;

/**
 * Sends a push notification to every active device registered for a user.
 * Implementations must never throw - a push-delivery failure must not
 * disrupt the caller, which has already persisted the in-app notification
 * row regardless of whether push delivery succeeds.
 */
public interface FcmPushNotificationService {

    void sendToUser(UUID userId, String title, String body, Map<String, String> data);
}
