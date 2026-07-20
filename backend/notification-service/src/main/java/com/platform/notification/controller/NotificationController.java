package com.platform.notification.controller;

import com.platform.notification.dto.response.NotificationResponse;
import com.platform.notification.security.SecurityContextUtil;
import com.platform.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * User-facing endpoints. Notifications are always system-generated
 * (see InternalNotificationController) - there is deliberately no endpoint
 * letting a client author their own.
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications for the current user")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List the current user's notifications, most recent first")
    public List<NotificationResponse> getNotifications() {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return notificationService.getForUser(userId);
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark a notification as read")
    public NotificationResponse markAsRead(@PathVariable UUID notificationId) {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        log.info("Marking notification {} as read for user {}", notificationId, userId);
        return notificationService.markAsRead(userId, notificationId);
    }
}
