package com.platform.notification.service.impl;

import com.platform.analytics.exception.ResourceNotFoundException;
import com.platform.notification.constant.DevicePlatform;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.dto.request.RegisterDeviceRequest;
import com.platform.notification.dto.response.DeviceTokenResponse;
import com.platform.notification.entity.DeviceToken;
import com.platform.notification.repository.DeviceTokenRepository;
import com.platform.notification.service.NotificationService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceImplTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DeviceTokenServiceImpl deviceTokenService;

    private UUID userId;
    private RegisterDeviceRequest request;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        request = RegisterDeviceRequest.builder().token("fcm-token-abc").platform(DevicePlatform.ANDROID).build();
    }

    @Test
    void register_firstDeviceEver_savesButDoesNotSendNewDeviceNotification() {
        when(deviceTokenRepository.existsByUserId(userId)).thenReturn(false);
        when(deviceTokenRepository.findByToken(request.getToken())).thenReturn(Optional.empty());
        when(deviceTokenRepository.save(any())).thenAnswer(inv -> {
            DeviceToken dt = inv.getArgument(0);
            dt.setId(UUID.randomUUID());
            return dt;
        });

        DeviceTokenResponse response = deviceTokenService.register(userId, request);

        assertThat(response.isActive()).isTrue();
        assertThat(response.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
        // Suppressed on the very first device to avoid a redundant
        // NEW_DEVICE_LOGIN alongside WELCOME right after signup.
        verify(notificationService, never()).notifyUser(any(), eq(NotificationType.NEW_DEVICE_LOGIN), any(), any(), any());
    }

    @Test
    void register_secondNewDeviceForExistingUser_sendsNewDeviceNotification() {
        when(deviceTokenRepository.existsByUserId(userId)).thenReturn(true);
        when(deviceTokenRepository.findByToken(request.getToken())).thenReturn(Optional.empty());
        when(deviceTokenRepository.save(any())).thenAnswer(inv -> {
            DeviceToken dt = inv.getArgument(0);
            dt.setId(UUID.randomUUID());
            return dt;
        });

        deviceTokenService.register(userId, request);

        verify(notificationService).notifyUser(eq(userId), eq(NotificationType.NEW_DEVICE_LOGIN), any(), any(), any());
    }

    @Test
    void register_sameUserSameTokenAgain_touchesRowButDoesNotNotify() {
        DeviceToken existing = DeviceToken.builder()
                .id(UUID.randomUUID()).userId(userId).token(request.getToken())
                .platform(DevicePlatform.ANDROID).active(true)
                .createdAt(LocalDateTime.now().minusDays(5)).lastUsedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(deviceTokenRepository.existsByUserId(userId)).thenReturn(true);
        when(deviceTokenRepository.findByToken(request.getToken())).thenReturn(Optional.of(existing));
        when(deviceTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        deviceTokenService.register(userId, request);

        verify(notificationService, never()).notifyUser(any(), eq(NotificationType.NEW_DEVICE_LOGIN), any(), any(), any());
    }

    @Test
    void register_tokenPreviouslyOwnedByDifferentUser_reassignsAndNotifiesNewOwner() {
        UUID previousOwner = UUID.randomUUID();
        DeviceToken existing = DeviceToken.builder()
                .id(UUID.randomUUID()).userId(previousOwner).token(request.getToken())
                .platform(DevicePlatform.ANDROID).active(true)
                .createdAt(LocalDateTime.now().minusDays(5)).lastUsedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(deviceTokenRepository.existsByUserId(userId)).thenReturn(true);
        when(deviceTokenRepository.findByToken(request.getToken())).thenReturn(Optional.of(existing));
        when(deviceTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        deviceTokenService.register(userId, request);

        verify(notificationService).notifyUser(eq(userId), eq(NotificationType.NEW_DEVICE_LOGIN), any(), any(), any());
        assertThat(existing.getUserId()).isEqualTo(userId);
    }

    @Test
    void unregister_ownedByCaller_deactivates() {
        DeviceToken existing = DeviceToken.builder()
                .id(UUID.randomUUID()).userId(userId).token("tok").platform(DevicePlatform.IOS)
                .active(true).createdAt(LocalDateTime.now()).lastUsedAt(LocalDateTime.now()).build();
        when(deviceTokenRepository.findByToken("tok")).thenReturn(Optional.of(existing));
        when(deviceTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        deviceTokenService.unregister(userId, "tok");

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void unregister_ownedByDifferentUser_throwsNotFound() {
        DeviceToken existing = DeviceToken.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).token("tok").platform(DevicePlatform.IOS)
                .active(true).createdAt(LocalDateTime.now()).lastUsedAt(LocalDateTime.now()).build();
        when(deviceTokenRepository.findByToken("tok")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> deviceTokenService.unregister(userId, "tok"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
