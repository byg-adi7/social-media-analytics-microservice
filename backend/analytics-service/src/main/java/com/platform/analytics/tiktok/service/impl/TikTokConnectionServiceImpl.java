package com.platform.analytics.tiktok.service.impl;

import com.platform.analytics.constant.AccountConnectionType;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.ConflictException;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.mapper.SocialAccountMapper;
import com.platform.analytics.repository.AnalyticsRepository;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.security.StateTokenService;
import com.platform.analytics.service.AnalyticsSyncService;
import com.platform.analytics.tiktok.TikTokProperties;
import com.platform.analytics.tiktok.api.TikTokApiClient;
import com.platform.analytics.tiktok.api.dto.TikTokTokenResponse;
import com.platform.analytics.tiktok.api.dto.TikTokUserInfoResponse;
import com.platform.analytics.tiktok.service.TikTokConnectionService;
import com.platform.analytics.tiktok.service.TikTokOAuthService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final AnalyticsRepository analyticsRepository;
    private final SocialAccountMapper socialAccountMapper;
    private final AnalyticsSyncService analyticsSyncService;
    private final TikTokProperties tikTokProperties;

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

        SocialAccount account = resolveAccountForConnection(userId, openId);

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
        account.setConnectionType(AccountConnectionType.OAUTH);
        account.setActive(true);

        SocialAccount saved = socialAccountRepository.save(account);
        log.info("Connected/updated real TikTok account {} (openId={}) for user {}", saved.getId(), openId, userId);

        analyticsSyncService.syncAccount(saved);

        return socialAccountMapper.toResponse(saved);
    }

    private SocialAccount resolveAccountForConnection(UUID userId, String openId) {
        Optional<SocialAccount> existing =
                socialAccountRepository.findByPlatformAndAccountId(Platform.TIKTOK, openId);

        if (existing.isEmpty()) {
            return SocialAccount.builder()
                    .userId(userId)
                    .platform(Platform.TIKTOK)
                    .accountId(openId)
                    .connectedAt(LocalDateTime.now())
                    .build();
        }

        SocialAccount existingAccount = existing.get();
        if (existingAccount.getUserId().equals(userId)) {
            if (!existingAccount.isActive()) {
                analyticsRepository.deleteBySocialAccountId(existingAccount.getId());
                existingAccount.setConnectedAt(LocalDateTime.now());
            }
            return existingAccount;
        }

        if (!existingAccount.isActive()) {
            analyticsRepository.deleteBySocialAccountId(existingAccount.getId());
            socialAccountRepository.delete(existingAccount);
            socialAccountRepository.flush();
            return SocialAccount.builder()
                    .userId(userId)
                    .platform(Platform.TIKTOK)
                    .accountId(openId)
                    .connectedAt(LocalDateTime.now())
                    .build();
        }

        throw new ConflictException("This TikTok account is already connected to another user");
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
