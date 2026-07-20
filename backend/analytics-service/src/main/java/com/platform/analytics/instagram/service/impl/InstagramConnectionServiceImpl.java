package com.platform.analytics.instagram.service.impl;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.instagram.InstagramProperties;
import com.platform.analytics.instagram.api.InstagramApiClient;
import com.platform.analytics.instagram.api.dto.InstagramLongLivedTokenResponse;
import com.platform.analytics.instagram.api.dto.InstagramProfileResponse;
import com.platform.analytics.instagram.api.dto.InstagramShortLivedTokenResponse;
import com.platform.analytics.instagram.service.InstagramConnectionService;
import com.platform.analytics.instagram.service.InstagramOAuthService;
import com.platform.analytics.mapper.SocialAccountMapper;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.security.StateTokenService;
import com.platform.analytics.service.AnalyticsSyncService;
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
public class InstagramConnectionServiceImpl implements InstagramConnectionService {

    private static final String PROFILE_FIELDS =
            "user_id,username,name,biography,followers_count,follows_count,media_count,profile_picture_url";

    private final InstagramOAuthService instagramOAuthService;
    private final InstagramApiClient instagramApiClient;
    private final StateTokenService stateTokenService;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialAccountMapper socialAccountMapper;
    private final AnalyticsSyncService analyticsSyncService;
    private final InstagramProperties instagramProperties;

    @Override
    public String getAuthorizationUrl(UUID userId) {
        assertIntegrationEnabled();
        return instagramOAuthService.buildAuthorizationUrl(userId);
    }

    @Override
    @Transactional
    public SocialAccountResponse completeConnection(String code, String state) {
        assertIntegrationEnabled();
        UUID userId = stateTokenService.verifyAndExtractUserId(state);

        log.info("Completing Instagram OAuth connection for user {}", userId);

        InstagramShortLivedTokenResponse shortLived = instagramOAuthService.exchangeCodeForShortLivedToken(code);
        if (shortLived.data() == null || shortLived.data().isEmpty()) {
            throw new ExternalApiException("Instagram returned no access token for the authorization code");
        }
        String shortLivedAccessToken = shortLived.data().get(0).accessToken();

        InstagramLongLivedTokenResponse longLived = instagramOAuthService.exchangeForLongLivedToken(shortLivedAccessToken);

        InstagramProfileResponse profile = fetchProfile(longLived.accessToken());

        String accountId = profile.userId();
        String username = profile.username() != null ? profile.username() : "Instagram User";
        String profileImage = profile.profilePictureUrl();

        SocialAccount account = socialAccountRepository
                .findByUserIdAndPlatformAndAccountId(userId, Platform.INSTAGRAM, accountId)
                .orElseGet(() -> SocialAccount.builder()
                        .userId(userId)
                        .platform(Platform.INSTAGRAM)
                        .accountId(accountId)
                        .connectedAt(LocalDateTime.now())
                        .build());

        account.setAccountName(profile.name() != null ? profile.name() : username);
        account.setUsername(username);
        account.setProfileImage(profileImage);
        account.setAccessToken(longLived.accessToken());

        // Instagram's long-lived token has no separate refresh token — the
        // access token itself is what gets refreshed (see
        // InstagramSocialMediaClient.resolveFreshAccessToken).
        if (longLived.expiresIn() != null) {
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(longLived.expiresIn()));
        }
        account.setActive(true);

        SocialAccount saved = socialAccountRepository.save(account);
        log.info("Connected/updated real Instagram account {} (igUserId={}) for user {}",
                saved.getId(), accountId, userId);

        analyticsSyncService.syncAccount(saved);

        return socialAccountMapper.toResponse(saved);
    }

    private InstagramProfileResponse fetchProfile(String accessToken) {
        try {
            return instagramApiClient.getProfile(PROFILE_FIELDS, accessToken);
        } catch (FeignException ex) {
            log.error("Failed to fetch profile during Instagram connect: {}", ex.getMessage());
            throw new ExternalApiException("Failed to fetch Instagram profile", ex);
        }
    }

    private void assertIntegrationEnabled() {
        if (!instagramProperties.isEnabled()) {
            throw new BadRequestException(
                    "The real Instagram integration is not enabled on this server (instagram.enabled=false). " +
                            "Connected accounts currently use simulated data via MockSocialMediaClient.");
        }
    }
}
