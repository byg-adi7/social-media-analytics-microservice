package com.platform.analytics.service.impl;

import com.platform.analytics.client.SocialMediaClient;
import com.platform.analytics.client.SocialMediaClientResolver;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.repository.AnalyticsRepository;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.service.AnalyticsSyncService;
import com.platform.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsSyncServiceImplTest {

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private AnalyticsRepository analyticsRepository;

    @Mock
    private SocialMediaClientResolver socialMediaClientResolver;

    @Mock
    private SocialMediaClient mockClient;

    @Mock
    private AnalyticsSyncService self;

    @Mock
    private NotificationService notificationService;

    private AnalyticsSyncServiceImpl analyticsSyncService;
    private SocialAccount account;

    @BeforeEach
    void setUp() {
        analyticsSyncService = new AnalyticsSyncServiceImpl(
                socialAccountRepository, analyticsRepository, socialMediaClientResolver,
                notificationService, self);

        account = SocialAccount.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .platform(Platform.YOUTUBE)
                .accountId("yt-123")
                .connectedAt(LocalDateTime.now())
                .active(true)
                .build();
    }

    @Test
    void syncAccount_fetchesMetrics_andPersistsAnalytics() {
        when(socialMediaClientResolver.resolve(Platform.YOUTUBE)).thenReturn(mockClient);
        when(mockClient.fetchDailyMetrics(eq(account), any())).thenReturn(
                new SocialMediaClient.DailyMetrics(1000, 50, 5000, 3000, 100, 2000, 120.5, 80, 20, 10, 5, 1));
        when(analyticsRepository.findBySocialAccountIdAndAnalyticsDate(eq(account.getId()), any()))
                .thenReturn(Optional.empty());

        analyticsSyncService.syncAccount(account);

        verify(analyticsRepository).save(any());
        verify(socialAccountRepository).save(account);
    }

    @Test
    void syncAllActiveAccounts_delegatesEachAccount_throughSelfProxy_notThis() {
        SocialAccount other = SocialAccount.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .platform(Platform.INSTAGRAM)
                .accountId("ig-456")
                .connectedAt(LocalDateTime.now())
                .active(true)
                .build();

        when(socialAccountRepository.findAllActiveAccounts()).thenReturn(List.of(account, other));

        analyticsSyncService.syncAllActiveAccounts();

        // Each account must be synced via the injected proxy (self), not a
        // direct this.syncAccount(...) call, so each one gets its own
        // transaction. If this regresses to a self-invocation, this
        // verification fails because `self` never gets called.
        verify(self).syncAccount(account);
        verify(self).syncAccount(other);
        verifyNoInteractions(mockClient);
    }

    @Test
    void syncAllActiveAccounts_notifiesOnFailure_butKeepsProcessingOtherAccounts() {
        SocialAccount other = SocialAccount.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .platform(Platform.INSTAGRAM)
                .accountId("ig-456")
                .connectedAt(LocalDateTime.now())
                .active(true)
                .build();

        when(socialAccountRepository.findAllActiveAccounts()).thenReturn(List.of(account, other));
        doThrow(new RuntimeException("external API down")).when(self).syncAccount(account);

        analyticsSyncService.syncAllActiveAccounts();

        verify(self).syncAccount(account);
        verify(self).syncAccount(other);
        verify(notificationService).notifyUser(eq(account.getUserId()), any(), any(), any(), any());
    }

    @Test
    void syncAllActiveAccounts_notificationCreateFails_doesNotBreakTheLoop() {
        when(socialAccountRepository.findAllActiveAccounts()).thenReturn(List.of(account));
        doThrow(new RuntimeException("sync failed")).when(self).syncAccount(account);
        doThrow(new RuntimeException("notification create failed"))
                .when(notificationService).notifyUser(any(), any(), any(), any(), any());

        // Must not throw - a notification-creation failure is best-effort
        // and must never propagate out of the scheduled sync loop.
        analyticsSyncService.syncAllActiveAccounts();

        verify(notificationService).notifyUser(eq(account.getUserId()), any(), any(), any(), any());
    }
}
