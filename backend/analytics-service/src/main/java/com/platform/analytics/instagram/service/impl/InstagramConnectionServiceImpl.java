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
    private final NotificationService notificationService;

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

        Optional<SocialAccount> existing =
                socialAccountRepository.findByUserIdAndPlatformAndAccountId(userId, Platform.INSTAGRAM, accountId);
        boolean isNewConnection = existing.isEmpty();

        // uk_platform_account_id is global (platform + account_id), not
        // scoped to this user - without this check, a different user
        // connecting the same Instagram account would only fail at
        // transaction-commit time, after syncAccount() below has already
        // run as an irreversible side effect.
        if (isNewConnection && socialAccountRepository.existsByPlatformAndAccountId(Platform.INSTAGRAM, accountId)) {
            throw new BadRequestException("This Instagram account is already connected to another account.");
        }

        SocialAccount account = existing.orElseGet(() -> SocialAccount.builder()
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

        SocialAccount saved = socialAccountRepository.saveAndFlush(account);
        log.info("Connected/updated real Instagram account {} (igUserId={}) for user {}",
                saved.getId(), accountId, userId);

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
                    "Your Instagram account was connected successfully.",
                    Map.of("accountId", account.getId().toString()));
        } catch (Exception ex) {
            log.warn("Failed to send account-connected notification for account {}: {}",
                    account.getId(), ex.getMessage());
        }
    }

    private InstagramProfileResponse fetchProfile(String accessToken) {
        try {
            return instagramApiClient.getProfile(PROFILE_FIELDS, accessToken);
        } catch (FeignException ex) {
            log.error("Failed to fetch profile during Instagram connect: HTTP {}", ex.status());
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
