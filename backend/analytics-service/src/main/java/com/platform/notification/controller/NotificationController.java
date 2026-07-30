package com.platform.notification.controller;

import com.platform.analytics.dto.response.PagedResponse;
import com.platform.notification.dto.response.NotificationResponse;
import com.platform.notification.dto.response.MarkAllReadResponse;
import com.platform.notification.dto.response.UnreadCountResponse;
import com.platform.analytics.security.SecurityContextUtil;
import com.platform.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * User-facing endpoints. Notifications are always system-generated
 * (fired internally by SocialAccountServiceImpl/AnalyticsSyncServiceImpl/
 * ReportServiceImpl via NotificationService.create) - there is deliberately
 * no endpoint letting a client author their own.
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications for the current user")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List the current user's notifications, most recent first",
            description = "Paginated: pass page/size query params (defaults: page=0, size=20).")
    public PagedResponse<NotificationResponse> getNotifications(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return notificationService.getForUser(userId, pageable);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get the current user's unread notification count",
            description = "Cheaper than fetching the full list just to render a badge number.")
    public UnreadCountResponse getUnreadCount() {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        return UnreadCountResponse.builder().unreadCount(notificationService.getUnreadCount(userId)).build();
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark a notification as read")
    public NotificationResponse markAsRead(@PathVariable UUID notificationId) {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        log.info("Marking notification {} as read for user {}", notificationId, userId);
        return notificationService.markAsRead(userId, notificationId);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all of the current user's notifications as read")
    public MarkAllReadResponse markAllAsRead() {
        UUID userId = SecurityContextUtil.getCurrentUserId();
        int updated = notificationService.markAllAsRead(userId);
        return MarkAllReadResponse.builder().markedAsRead(updated).build();
    }
}
