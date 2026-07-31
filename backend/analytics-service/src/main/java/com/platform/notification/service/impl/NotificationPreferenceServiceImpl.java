package com.platform.notification.service.impl;

import com.platform.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.platform.notification.dto.response.NotificationPreferenceResponse;
import com.platform.notification.entity.NotificationPreference;
import com.platform.notification.repository.NotificationPreferenceRepository;
import com.platform.notification.service.NotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;

    @Override
    public NotificationPreferenceResponse getForUser(UUID userId) {
        return notificationPreferenceRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> NotificationPreferenceResponse.builder()
                        .pushEnabled(true)
                        .emailEnabled(true)
                        .build());
    }

    @Override
    @Transactional
    public NotificationPreferenceResponse update(UUID userId, UpdateNotificationPreferenceRequest request) {
        NotificationPreference preference = notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> NotificationPreference.builder()
                        .userId(userId)
                        .createdAt(LocalDateTime.now())
                        .build());

        preference.setPushEnabled(request.getPushEnabled());
        preference.setEmailEnabled(request.getEmailEnabled());
        preference.setUpdatedAt(LocalDateTime.now());

        NotificationPreference saved = notificationPreferenceRepository.save(preference);
        log.info("Updated notification preferences for user {}: pushEnabled={}, emailEnabled={}",
                userId, saved.isPushEnabled(), saved.isEmailEnabled());
        return toResponse(saved);
    }

    @Override
    public boolean isPushEnabled(UUID userId) {
        return notificationPreferenceRepository.findByUserId(userId)
                .map(NotificationPreference::isPushEnabled)
                .orElse(true);
    }

    @Override
    @Transactional
    public void deleteAllForUser(UUID userId) {
        notificationPreferenceRepository.deleteAllByUserId(userId);
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference preference) {
        return NotificationPreferenceResponse.builder()
                .pushEnabled(preference.isPushEnabled())
                .emailEnabled(preference.isEmailEnabled())
                .build();
    }
}
