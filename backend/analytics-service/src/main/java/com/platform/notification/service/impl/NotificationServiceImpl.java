package com.platform.notification.service.impl;

import com.platform.analytics.dto.response.PagedResponse;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.dto.response.NotificationResponse;
import com.platform.notification.entity.Notification;
import com.platform.analytics.exception.ResourceNotFoundException;
import com.platform.notification.repository.NotificationRepository;
import com.platform.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public NotificationResponse create(UUID userId, NotificationType type, String message) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .message(message)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Created {} notification {} for user {}", type, saved.getId(), userId);
        return toResponse(saved);
    }

    @Override
    public PagedResponse<NotificationResponse> getForUser(UUID userId, Pageable pageable) {
        return PagedResponse.of(notificationRepository.findAllByUserId(userId, pageable).map(this::toResponse));
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Notification", notificationId));

        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .message(notification.getMessage())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
