package com.platform.analytics.service.impl;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.request.ConnectAccountRequest;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.ResourceNotFoundException;
import com.platform.analytics.mapper.SocialAccountMapper;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.service.AnalyticsSyncService;
import com.platform.analytics.validator.PlatformValidator;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocialAccountServiceImplTest {

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private SocialAccountMapper socialAccountMapper;

    @Mock
    private PlatformValidator platformValidator;

    @Mock
    private AnalyticsSyncService analyticsSyncService;

    @InjectMocks
    private SocialAccountServiceImpl socialAccountService;

    private UUID userId;
    private UUID accountId;
    private SocialAccount account;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        account = SocialAccount.builder()
                .id(accountId)
                .userId(userId)
                .platform(Platform.YOUTUBE)
                .accountId("yt-123")
                .accountName("My Channel")
                .connectedAt(LocalDateTime.now())
                .active(true)
                .build();
    }

    @Test
    void connectAccount_savesNewAccount_andTriggersInitialSync() {
        ConnectAccountRequest request = ConnectAccountRequest.builder()
                .platform(Platform.YOUTUBE)
                .accountId("yt-123")
                .accountName("My Channel")
                .accessToken("token")
                .build();

        when(socialAccountRepository.existsByUserIdAndPlatformAndAccountId(userId, Platform.YOUTUBE, "yt-123"))
                .thenReturn(false);
        when(socialAccountRepository.save(any(SocialAccount.class))).thenReturn(account);
        when(socialAccountMapper.toResponse(account)).thenReturn(SocialAccountResponse.builder().id(accountId).build());

        SocialAccountResponse response = socialAccountService.connectAccount(userId, request);

        assertThat(response.getId()).isEqualTo(accountId);
        verify(platformValidator).validate(Platform.YOUTUBE);
        verify(analyticsSyncService).syncAccount(account);
    }

    @Test
    void connectAccount_throwsBadRequest_whenAlreadyConnected() {
        ConnectAccountRequest request = ConnectAccountRequest.builder()
                .platform(Platform.YOUTUBE)
                .accountId("yt-123")
                .accountName("My Channel")
                .accessToken("token")
                .build();

        when(socialAccountRepository.existsByUserIdAndPlatformAndAccountId(userId, Platform.YOUTUBE, "yt-123"))
                .thenReturn(true);

        assertThatThrownBy(() -> socialAccountService.connectAccount(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already connected");

        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void getAccountById_throwsNotFound_whenAccountDoesNotExist() {
        when(socialAccountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> socialAccountService.getAccountById(userId, accountId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void disconnectAccount_deletesAccount_whenFound() {
        when(socialAccountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        socialAccountService.disconnectAccount(userId, accountId);

        verify(socialAccountRepository).delete(account);
    }

    @Test
    void syncAccount_delegatesToAnalyticsSyncService() {
        when(socialAccountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(socialAccountMapper.toResponse(account)).thenReturn(SocialAccountResponse.builder().id(accountId).build());

        SocialAccountResponse response = socialAccountService.syncAccount(userId, accountId);

        assertThat(response.getId()).isEqualTo(accountId);
        verify(analyticsSyncService).syncAccount(account);
    }
}
