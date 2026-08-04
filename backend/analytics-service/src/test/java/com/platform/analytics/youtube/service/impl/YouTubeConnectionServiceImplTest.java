package com.platform.analytics.youtube.service.impl;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.ConflictException;
import com.platform.analytics.mapper.SocialAccountMapper;
import com.platform.analytics.repository.AnalyticsRepository;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.security.StateTokenService;
import com.platform.analytics.service.AnalyticsSyncService;
import com.platform.analytics.youtube.YouTubeProperties;
import com.platform.analytics.youtube.api.YouTubeDataApiClient;
import com.platform.analytics.youtube.api.dto.GoogleTokenResponse;
import com.platform.analytics.youtube.api.dto.YouTubeChannelListResponse;
import com.platform.analytics.youtube.service.YouTubeOAuthService;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeConnectionServiceImplTest {

    @Mock
    private YouTubeOAuthService youTubeOAuthService;

    @Mock
    private YouTubeDataApiClient youTubeDataApiClient;

    @Mock
    private StateTokenService stateTokenService;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private AnalyticsRepository analyticsRepository;

    @Mock
    private SocialAccountMapper socialAccountMapper;

    @Mock
    private AnalyticsSyncService analyticsSyncService;

    @Mock
    private YouTubeProperties youTubeProperties;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private YouTubeConnectionServiceImpl youTubeConnectionService;

    private UUID userId;
    private static final String STATE = "some-state";
    private static final String CODE = "some-code";
    private static final String CHANNEL_ID = "yt-channel-1";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        when(youTubeProperties.isEnabled()).thenReturn(true);
        when(stateTokenService.verifyAndExtractUserId(STATE)).thenReturn(userId);
        when(youTubeOAuthService.exchangeCodeForTokens(CODE)).thenReturn(
                new GoogleTokenResponse("access-token", "refresh-token", 3600L, "scope", "Bearer"));

        YouTubeChannelListResponse.Item item = new YouTubeChannelListResponse.Item(
                CHANNEL_ID, new YouTubeChannelListResponse.Snippet("My Channel", "desc", null), null);
        when(youTubeDataApiClient.getMyChannel(anyString(), anyString(), anyBoolean()))
                .thenReturn(new YouTubeChannelListResponse.Response(List.of(item)));

        lenient().when(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
                .thenAnswer(invocation -> {
                    SocialAccount a = invocation.getArgument(0);
                    if (a.getId() == null) {
                        a.setId(UUID.randomUUID());
                    }
                    return a;
                });
        lenient().when(socialAccountMapper.toResponse(any(SocialAccount.class)))
                .thenReturn(SocialAccountResponse.builder().build());
    }

    @Test
    void completeConnection_newAccount_sendsAccountConnectedNotification() {
        // Regression test: the real Google OAuth connect flow used to skip
        // the ACCOUNT_CONNECTED notification that the mock/manual connect
        // flow (SocialAccountServiceImpl) always sends - inconsistent once
        // real YouTube became the primary tested integration.
        when(socialAccountRepository.findByPlatformAndAccountId(Platform.YOUTUBE, CHANNEL_ID))
                .thenReturn(Optional.empty());

        youTubeConnectionService.completeConnection(CODE, STATE);

        verify(notificationService).notifyUser(eq(userId), eq(NotificationType.ACCOUNT_CONNECTED), anyString(), anyString(), any());
    }

    @Test
    void completeConnection_existingAccount_doesNotSendDuplicateNotification() {
        SocialAccount existing = SocialAccount.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .platform(Platform.YOUTUBE)
                .accountId(CHANNEL_ID)
                .connectedAt(LocalDateTime.now().minusDays(30))
                .active(true)
                .build();
        when(socialAccountRepository.findByPlatformAndAccountId(Platform.YOUTUBE, CHANNEL_ID))
                .thenReturn(Optional.of(existing));

        youTubeConnectionService.completeConnection(CODE, STATE);

        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any());
    }

    @Test
    void completeConnection_newAccount_syncsAnalyticsAfterSaving() {
        when(socialAccountRepository.findByPlatformAndAccountId(Platform.YOUTUBE, CHANNEL_ID))
                .thenReturn(Optional.empty());

        youTubeConnectionService.completeConnection(CODE, STATE);

        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(analyticsSyncService).syncAccount(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(CHANNEL_ID);
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void completeConnection_inactiveSameUserAccount_reactivatesExistingRecord() {
        UUID existingId = UUID.randomUUID();
        SocialAccount existing = SocialAccount.builder()
                .id(existingId)
                .userId(userId)
                .platform(Platform.YOUTUBE)
                .accountId(CHANNEL_ID)
                .connectedAt(LocalDateTime.now().minusDays(30))
                .active(false)
                .build();
        when(socialAccountRepository.findByPlatformAndAccountId(Platform.YOUTUBE, CHANNEL_ID))
                .thenReturn(Optional.of(existing));

        youTubeConnectionService.completeConnection(CODE, STATE);

        verify(analyticsRepository).deleteBySocialAccountId(existingId);
        verify(socialAccountRepository).saveAndFlush(existing);
        assertThat(existing.isActive()).isTrue();
        assertThat(existing.getAccessToken()).isEqualTo("access-token");
        verify(notificationService).notifyUser(eq(userId), eq(NotificationType.ACCOUNT_CONNECTED), anyString(), anyString(), any());
    }

    @Test
    void completeConnection_inactiveDifferentUserStaleRecord_deletesThenCreatesNewRecord() {
        UUID staleId = UUID.randomUUID();
        SocialAccount stale = SocialAccount.builder()
                .id(staleId)
                .userId(UUID.randomUUID())
                .platform(Platform.YOUTUBE)
                .accountId(CHANNEL_ID)
                .connectedAt(LocalDateTime.now().minusDays(30))
                .active(false)
                .build();
        when(socialAccountRepository.findByPlatformAndAccountId(Platform.YOUTUBE, CHANNEL_ID))
                .thenReturn(Optional.of(stale));

        youTubeConnectionService.completeConnection(CODE, STATE);

        verify(analyticsRepository).deleteBySocialAccountId(staleId);
        verify(socialAccountRepository).delete(stale);
        verify(socialAccountRepository).flush();

        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        SocialAccount saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getAccountId()).isEqualTo(CHANNEL_ID);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void completeConnection_activeDifferentUserAccount_throwsConflict() {
        SocialAccount existing = SocialAccount.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .platform(Platform.YOUTUBE)
                .accountId(CHANNEL_ID)
                .connectedAt(LocalDateTime.now().minusDays(30))
                .active(true)
                .build();
        when(socialAccountRepository.findByPlatformAndAccountId(Platform.YOUTUBE, CHANNEL_ID))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> youTubeConnectionService.completeConnection(CODE, STATE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already connected");

        verify(socialAccountRepository, never()).saveAndFlush(any());
    }
}
