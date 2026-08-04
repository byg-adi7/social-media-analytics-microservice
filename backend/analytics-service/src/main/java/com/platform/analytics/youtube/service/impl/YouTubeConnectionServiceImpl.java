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
import com.platform.notification.constant.NotificationType;
import com.platform.notification.service.NotificationService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
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
    private final NotificationService notificationService;

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

        Optional<SocialAccount> existing =
                socialAccountRepository.findByUserIdAndPlatformAndAccountId(userId, Platform.YOUTUBE, channelId);
        boolean isNewConnection = existing.isEmpty();

        // uk_platform_account_id is global (platform + account_id), not
        // scoped to this user - the check above only rules out *this* user
        // already having this channel. Without this second check, a
        // different user connecting the same channel would fall through to
        // save() below, which only fails at transaction-commit time (after
        // syncAccount()/notifyAccountConnected() have already run as
        // irreversible side effects) - the account-connected push would
        // fire for a connection that then rolls back.
        if (isNewConnection && socialAccountRepository.existsByPlatformAndAccountId(Platform.YOUTUBE, channelId)) {
            throw new BadRequestException("This YouTube channel is already connected to another account.");
        }

        SocialAccount account = existing.orElseGet(() -> SocialAccount.builder()
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

        // Flushed immediately (not deferred to transaction commit) so a
        // genuinely concurrent duplicate-channel insert - two users
        // connecting the same channel at the same instant, both passing the
        // check above - fails right here, before syncAccount()/
        // notifyAccountConnected() run, instead of at commit time.
        SocialAccount saved = socialAccountRepository.saveAndFlush(account);
        log.info("Connected/updated real YouTube account {} (channel={}) for user {}", saved.getId(), channelId, userId);

        analyticsSyncService.syncAccount(saved);

        if (isNewConnection) {
            notifyAccountConnected(saved);
        }

        return socialAccountMapper.toResponse(saved);
    }

    /**
     * Best-effort, same reasoning as SocialAccountServiceImpl's identical
     * method: a failure creating the notification must never fail an
     * otherwise-successful account connection.
     */
    private void notifyAccountConnected(SocialAccount account) {
        try {
            notificationService.notifyUser(
                    account.getUserId(),
                    NotificationType.ACCOUNT_CONNECTED,
                    "Account connected",
                    "Your YouTube account was connected successfully.",
                    Map.of("accountId", account.getId().toString()));
        } catch (Exception ex) {
            log.warn("Failed to send account-connected notification for account {}: {}",
                    account.getId(), ex.getMessage());
        }
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
