package com.platform.analytics.tiktok.service.impl;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.mapper.SocialAccountMapper;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.security.StateTokenService;
import com.platform.analytics.service.AnalyticsSyncService;
import com.platform.analytics.tiktok.TikTokProperties;
import com.platform.analytics.tiktok.api.TikTokApiClient;
import com.platform.analytics.tiktok.api.dto.TikTokTokenResponse;
import com.platform.analytics.tiktok.api.dto.TikTokUserInfoResponse;
import com.platform.analytics.tiktok.service.TikTokConnectionService;
import com.platform.analytics.tiktok.service.TikTokOAuthService;
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
public class TikTokConnectionServiceImpl implements TikTokConnectionService {

    private static final String PROFILE_FIELDS =
            "open_id,display_name,username,avatar_url,follower_count,following_count,likes_count,video_count";

    private final TikTokOAuthService tikTokOAuthService;
    private final TikTokApiClient tikTokApiClient;
    private final StateTokenService stateTokenService;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialAccountMapper socialAccountMapper;
    private final AnalyticsSyncService analyticsSyncService;
    private final TikTokProperties tikTokProperties;
    private final NotificationService notificationService;

    @Override
    public String getAuthorizationUrl(UUID userId) {
        assertIntegrationEnabled();
        return tikTokOAuthService.buildAuthorizationUrl(userId);
    }

    @Override
    @Transactional
    public SocialAccountResponse completeConnection(String code, String state) {
        assertIntegrationEnabled();
        UUID userId = stateTokenService.verifyAndExtractUserId(state);

        log.info("Completing TikTok OAuth connection for user {}", userId);

        TikTokTokenResponse tokens = tikTokOAuthService.exchangeCodeForTokens(code);
        TikTokUserInfoResponse.User profile = fetchProfile(tokens.accessToken());

        String openId = profile.openId() != null ? profile.openId() : tokens.openId();
        if (openId == null || openId.isBlank()) {
            throw new ExternalApiException("TikTok API returned no open_id for the connecting user");
        }

        String accountName = profile.displayName() != null ? profile.displayName()
                : (profile.username() != null ? profile.username() : "TikTok Account");

        Optional<SocialAccount> existing =
                socialAccountRepository.findByUserIdAndPlatformAndAccountId(userId, Platform.TIKTOK, openId);
        boolean isNewConnection = existing.isEmpty();

        // uk_platform_account_id is global (platform + account_id), not
        // scoped to this user - without this check, a different user
        // connecting the same TikTok account would only fail at
        // transaction-commit time, after syncAccount() below has already
        // run as an irreversible side effect.
        if (isNewConnection && socialAccountRepository.existsByPlatformAndAccountId(Platform.TIKTOK, openId)) {
            throw new BadRequestException("This TikTok account is already connected to another account.");
        }

        SocialAccount account = existing.orElseGet(() -> SocialAccount.builder()
                        .userId(userId)
                        .platform(Platform.TIKTOK)
                        .accountId(openId)
                        .connectedAt(LocalDateTime.now())
                        .build());

        account.setAccountName(accountName);
        account.setUsername(profile.username());
        account.setProfileImage(profile.avatarUrl());
        account.setAccessToken(tokens.accessToken());

        if (tokens.refreshToken() != null && !tokens.refreshToken().isBlank()) {
            account.setRefreshToken(tokens.refreshToken());
        }

        if (tokens.expiresInSeconds() != null) {
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(tokens.expiresInSeconds()));
        }
        account.setActive(true);

        SocialAccount saved = socialAccountRepository.saveAndFlush(account);
        log.info("Connected/updated real TikTok account {} (openId={}) for user {}", saved.getId(), openId, userId);

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
                    "Your TikTok account was connected successfully.",
                    Map.of("accountId", account.getId().toString()));
        } catch (Exception ex) {
            log.warn("Failed to send account-connected notification for account {}: {}",
                    account.getId(), ex.getMessage());
        }
    }

    private TikTokUserInfoResponse.User fetchProfile(String accessToken) {
        try {
            TikTokUserInfoResponse response = tikTokApiClient.getUserInfo("Bearer " + accessToken, PROFILE_FIELDS);

            if (response == null || response.data() == null || response.data().user() == null) {
                throw new ExternalApiException("TikTok API returned no user profile for the connecting account");
            }
            return response.data().user();
        } catch (FeignException ex) {
            log.error("Failed to fetch profile during TikTok connect: {}", ex.getMessage());
            throw new ExternalApiException("Failed to fetch TikTok profile", ex);
        }
    }

    private void assertIntegrationEnabled() {
        if (!tikTokProperties.isEnabled()) {
            throw new BadRequestException(
                    "The real TikTok integration is not enabled on this server (tiktok.enabled=false). " +
                            "Connected accounts currently use simulated data via MockSocialMediaClient.");
        }
    }
}
