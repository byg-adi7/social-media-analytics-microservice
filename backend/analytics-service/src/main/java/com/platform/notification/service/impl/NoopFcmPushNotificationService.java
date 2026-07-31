package com.platform.notification.service.impl;

import com.platform.notification.service.FcmPushNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Default push sender when firebase.enabled is false (or unset) - logs and
 * does nothing, exactly like MockSocialMediaClient stands in for a real
 * platform integration. Lets the rest of the notification pipeline (DB
 * persistence, in-app list, unread count) work fully without any Firebase
 * credentials configured.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopFcmPushNotificationService implements FcmPushNotificationService {

    @Override
    @Async("notificationTaskExecutor")
    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        log.debug("Firebase push disabled (firebase.enabled=false) - skipping push for user {}: {}", userId, title);
    }
}
