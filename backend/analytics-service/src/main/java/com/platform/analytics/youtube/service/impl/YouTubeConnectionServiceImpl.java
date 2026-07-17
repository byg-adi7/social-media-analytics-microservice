package com.platform.analytics.youtube.service.impl;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.mapper.SocialAccountMapper;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.security.StateTokenService;
import com.platform.analytics.service.AnalyticsSyncService;
import com.platform.analytics.youtube.YouTubeProperties;
import com.platform.analytics.youtube.api.YouTubeDataApiClient;
import com.platform.analytics.youtube.api.dto.GoogleTokenResponse;
import com.platform.analytics.youtube.api.dto.YouTubeChannelListResponse;
import com.platform.analytics.youtube.service.YouTubeConnectionService;
import com.platform.analytics.youtube.service.YouTubeOAuthService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeConnectionServiceImpl implements YouTubeConnectionService {

    private static final String CHANNEL_PART = "snippet,statistics";

    private final YouTubeOAuthService youTubeOAuthService;
    private final YouTubeDataApiClient youTubeDataApiClient;
    private final StateTokenService stateTokenService;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialAccountMapper socialAccountMapper;
    private final AnalyticsSyncService analyticsSyncService;
    private final YouTubeProperties youTubeProperties;

    @Override
    public String getAuthorizationUrl(UUID userId) {
        assertIntegrationEnabled();
        return youTubeOAuthService.buildAuthorizationUrl(userId);
    }

    @Override
    @Transactional
    public SocialAccountResponse completeConnection(String code, String state) {
        assertIntegrationEnabled();
        UUID userId = stateTokenService.verifyAndExtractUserId(state);

        log.info("Completing YouTube OAuth connection for user {}", userId);

        GoogleTokenResponse tokens = youTubeOAuthService.exchangeCodeForTokens(code);
        YouTubeChannelListResponse.Item channel = fetchChannelIdentity(tokens.accessToken());

        String channelId = channel.id();
        String channelTitle = channel.snippet() != null ? channel.snippet().title() : "YouTube Channel";
        String profileImage = (channel.snippet() != null && channel.snippet().thumbnails() != null
                && channel.snippet().thumbnails().defaultThumb() != null)
                ? channel.snippet().thumbnails().defaultThumb().url()
                : null;

        SocialAccount account = socialAccountRepository
                .findByUserIdAndPlatformAndAccountId(userId, Platform.YOUTUBE, channelId)
                .orElseGet(() -> SocialAccount.builder()
                        .userId(userId)
                        .platform(Platform.YOUTUBE)
                        .accountId(channelId)
                        .connectedAt(LocalDateTime.now())
                        .build());

        account.setAccountName(channelTitle);
        account.setUsername(channelTitle);
        account.setProfileImage(profileImage);
        account.setAccessToken(tokens.accessToken());

        // Google only returns refresh_token on the very first consent for a
        // given app+account; keep the previously stored one on reconnects
        // where it's omitted.
        if (tokens.refreshToken() != null && !tokens.refreshToken().isBlank()) {
            account.setRefreshToken(tokens.refreshToken());
        }

        if (tokens.expiresInSeconds() != null) {
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(tokens.expiresInSeconds()));
        }
        account.setActive(true);

        SocialAccount saved = socialAccountRepository.save(account);
        log.info("Connected/updated real YouTube account {} (channel={}) for user {}", saved.getId(), channelId, userId);

        analyticsSyncService.syncAccount(saved);

        return socialAccountMapper.toResponse(saved);
    }

    private YouTubeChannelListResponse.Item fetchChannelIdentity(String accessToken) {
        try {
            YouTubeChannelListResponse.Response response = youTubeDataApiClient.getMyChannel(
                    "Bearer " + accessToken, CHANNEL_PART, true);

            if (response == null || response.items() == null || response.items().isEmpty()) {
                throw new ExternalApiException("YouTube API returned no channel for the connecting user");
            }
            return response.items().get(0);
        } catch (FeignException ex) {
            log.error("Failed to fetch channel identity during YouTube connect: {}", ex.getMessage());
            throw new ExternalApiException("Failed to fetch YouTube channel identity", ex);
        }
    }

    private void assertIntegrationEnabled() {
        if (!youTubeProperties.isEnabled()) {
            throw new BadRequestException(
                    "The real YouTube integration is not enabled on this server (youtube.enabled=false). " +
                            "Connected accounts currently use simulated data via MockSocialMediaClient.");
        }
    }
}
