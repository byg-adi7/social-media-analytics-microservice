package com.platform.notification.service.impl;

import com.platform.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.platform.notification.dto.response.NotificationPreferenceResponse;
import com.platform.notification.entity.NotificationPreference;
import com.platform.notification.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceImplTest {

    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @InjectMocks
    private NotificationPreferenceServiceImpl notificationPreferenceService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void getForUser_noRowYet_defaultsToBothEnabled() {
        when(notificationPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        NotificationPreferenceResponse response = notificationPreferenceService.getForUser(userId);

        assertThat(response.isPushEnabled()).isTrue();
        assertThat(response.isEmailEnabled()).isTrue();
    }

    @Test
    void update_noRowYet_createsOne() {
        when(notificationPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(notificationPreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferenceResponse response = notificationPreferenceService.update(
                userId, UpdateNotificationPreferenceRequest.builder().pushEnabled(false).emailEnabled(true).build());

        assertThat(response.isPushEnabled()).isFalse();
        assertThat(response.isEmailEnabled()).isTrue();
    }

    @Test
    void update_existingRow_overwritesValues() {
        NotificationPreference existing = NotificationPreference.builder()
                .id(UUID.randomUUID()).userId(userId).pushEnabled(true).emailEnabled(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(notificationPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(notificationPreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferenceResponse response = notificationPreferenceService.update(
                userId, UpdateNotificationPreferenceRequest.builder().pushEnabled(false).emailEnabled(false).build());

        assertThat(response.isPushEnabled()).isFalse();
        assertThat(response.isEmailEnabled()).isFalse();
    }

    @Test
    void isPushEnabled_noRowYet_defaultsTrue() {
        when(notificationPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(notificationPreferenceService.isPushEnabled(userId)).isTrue();
    }

    @Test
    void isPushEnabled_rowDisablesPush_returnsFalse() {
        NotificationPreference existing = NotificationPreference.builder()
                .id(UUID.randomUUID()).userId(userId).pushEnabled(false).emailEnabled(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(notificationPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        assertThat(notificationPreferenceService.isPushEnabled(userId)).isFalse();
    }
}
