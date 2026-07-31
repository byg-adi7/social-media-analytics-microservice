package com.platform.notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analytics.dto.response.PagedResponse;
import com.platform.analytics.exception.ResourceNotFoundException;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.dto.response.NotificationResponse;
import com.platform.notification.entity.Notification;
import com.platform.notification.repository.NotificationRepository;
import com.platform.notification.service.FcmPushNotificationService;
import com.platform.notification.service.NotificationPreferenceService;
import com.platform.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService notificationPreferenceService;
    private final FcmPushNotificationService fcmPushNotificationService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public NotificationResponse createNotification(UUID userId, NotificationType type, String title, String message,
                                                     Map<String, Object> data) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .data(serialize(data))
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Created {} notification {} for user {}", type, saved.getId(), userId);
        return toResponse(saved);
    }

    @Override
    public void notifyUser(UUID userId, NotificationType type, String title, String message, Map<String, Object> data) {
        createNotification(userId, type, title, message, data);

        if (!notificationPreferenceService.isPushEnabled(userId)) {
            log.debug("Push disabled by preference for user {} - skipping push for {} notification", userId, type);
            return;
        }

        // fcmPushNotificationService is a distinct Spring bean (not `this`),
        // so its @Async sendToUser genuinely runs on the async executor -
        // see AnalyticsSyncServiceImpl's self-injection comment for why a
        // same-class call would silently NOT do this.
        fcmPushNotificationService.sendToUser(userId, title, message, Map.of("type", type.name()));
    }

    @Override
    public PagedResponse<NotificationResponse> getForUser(UUID userId, Pageable pageable) {
        return PagedResponse.of(notificationRepository.findAllByUserId(userId, pageable).map(this::toResponse));
    }

    @Override
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Notification", notificationId));

        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        return toResponse(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID userId) {
        int updated = notificationRepository.markAllAsReadForUser(userId, LocalDateTime.now());
        log.info("Marked {} notification(s) as read for user {}", updated, userId);
        return updated;
    }

    @Override
    @Transactional
    public void deleteAllForUser(UUID userId) {
        notificationRepository.deleteAllByUserId(userId);
    }

    private String serialize(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize notification data payload: {}", ex.getMessage());
            return null;
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .data(notification.getData())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }
}
