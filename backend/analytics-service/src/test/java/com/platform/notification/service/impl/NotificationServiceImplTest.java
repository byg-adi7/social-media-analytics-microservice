package com.platform.notification.service.impl;

import com.platform.notification.constant.NotificationType;
import com.platform.notification.dto.response.NotificationResponse;
import com.platform.notification.entity.Notification;
import com.platform.analytics.dto.response.PagedResponse;
import com.platform.analytics.exception.ResourceNotFoundException;
import com.platform.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void create_savesAndReturnsUnreadNotification() {
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        NotificationResponse response = notificationService.create(
                userId, NotificationType.ACCOUNT_CONNECTED, "Your YouTube account was connected");

        assertThat(response.getId()).isNotNull();
        assertThat(response.isRead()).isFalse();
        assertThat(response.getType()).isEqualTo(NotificationType.ACCOUNT_CONNECTED);
        assertThat(response.getMessage()).isEqualTo("Your YouTube account was connected");
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
    void markAsRead_flipsReadFlag() {
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
    }

    @Test
    void markAsRead_throwsWhenNotFoundOrNotOwnedByUser() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(userId, notificationId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(notificationRepository, org.mockito.Mockito.never()).save(any());
    }
}
