package com.platform.analytics.client;

import com.platform.analytics.dto.request.CreateNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Fires notifications for real events (account connected, sync failure) via
 * the Notification Service's internal-only endpoint, authenticated with a
 * shared API key rather than a user JWT - these events happen with no
 * end-user request in flight (e.g. a scheduled sync job).
 */
@FeignClient(name = "notification-service", url = "${notification-service.url}")
public interface NotificationServiceClient {

    @PostMapping("/internal/notifications")
    void createNotification(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @RequestBody CreateNotificationRequest request);
}
