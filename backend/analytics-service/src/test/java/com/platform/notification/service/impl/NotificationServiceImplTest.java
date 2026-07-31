package com.platform.notification.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.dto.response.NotificationResponse;
import com.platform.notification.entity.Notification;
import com.platform.analytics.dto.response.PagedResponse;
import com.platform.analytics.exception.ResourceNotFoundException;
import com.platform.notification.repository.NotificationRepository;
import com.platform.notification.service.FcmPushNotificationService;
import com.platform.notification.service.NotificationPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceService notificationPreferenceService;

    @Mock
    private FcmPushNotificationService fcmPushNotificationService;

    private NotificationServiceImpl notificationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        notificationService = new NotificationServiceImpl(
                notificationRepository, notificationPreferenceService, fcmPushNotificationService, new ObjectMapper());
    }

    @Test
    void createNotification_savesAndReturnsUnreadNotification() {
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        NotificationResponse response = notificationService.createNotification(
                userId, NotificationType.ACCOUNT_CONNECTED, "Account connected",
                "Your YouTube account was connected", null);

        assertThat(response.getId()).isNotNull();
        assertThat(response.isRead()).isFalse();
        assertThat(response.getType()).isEqualTo(NotificationType.ACCOUNT_CONNECTED);
        assertThat(response.getTitle()).isEqualTo("Account connected");
        assertThat(response.getMessage()).isEqualTo("Your YouTube account was connected");
    }

    @Test
    void createNotification_doesNotTouchPushChannel() {
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createNotification(userId, NotificationType.ACCOUNT_CONNECTED, "t", "m", null);

        verify(fcmPushNotificationService, never()).sendToUser(any(), any(), any(), any());
    }

    @Test
    void notifyUser_pushEnabled_persistsAndSendsPush() {
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationPreferenceService.isPushEnabled(userId)).thenReturn(true);

        notificationService.notifyUser(userId, NotificationType.SYNC_SUCCESS, "Sync complete", "All good",
                Map.of("accountId", "abc"));

        verify(notificationRepository).save(any());
        verify(fcmPushNotificationService).sendToUser(eq(userId), eq("Sync complete"), eq("All good"), anyMap());
    }

    @Test
    void notifyUser_pushDisabledByPreference_persistsButSkipsPush() {
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationPreferenceService.isPushEnabled(userId)).thenReturn(false);

        notificationService.notifyUser(userId, NotificationType.SYNC_SUCCESS, "Sync complete", "All good", null);

        verify(notificationRepository).save(any());
        verify(fcmPushNotificationService, never()).sendToUser(any(), any(), any(), any());
    }

    @Test
    void getForUser_returnsMappedPage() {
        Notification n = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(NotificationType.SYNC_FAILURE)
                .message("Sync failed")
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(notificationRepository.findAllByUserId(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(n), pageable, 1));

        PagedResponse<NotificationResponse> result = notificationService.getForUser(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getType()).isEqualTo(NotificationType.SYNC_FAILURE);
    }

    @Test
    void markAsRead_flipsReadFlagAndSetsReadAt() {
        UUID notificationId = UUID.randomUUID();
        Notification n = Notification.builder()
                .id(notificationId)
                .userId(userId)
                .type(NotificationType.REPORT_READY)
                .message("Report ready")
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(userId, notificationId);

        assertThat(response.isRead()).isTrue();
        assertThat(response.getReadAt()).isNotNull();
    }

    @Test
    void markAsRead_throwsWhenNotFoundOrNotOwnedByUser() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(userId, notificationId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(notificationRepository, never()).save(any());
    }
}
