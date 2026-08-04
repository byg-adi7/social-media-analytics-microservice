package com.platform.analytics.spotify.service.impl;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.BadRequestException;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.mapper.SocialAccountMapper;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.security.StateTokenService;
import com.platform.analytics.service.AnalyticsSyncService;
import com.platform.analytics.spotify.SpotifyProperties;
import com.platform.analytics.spotify.api.SpotifyApiClient;
import com.platform.analytics.spotify.api.dto.SpotifyTokenResponse;
import com.platform.analytics.spotify.api.dto.SpotifyUserProfileResponse;
import com.platform.analytics.spotify.service.SpotifyConnectionService;
import com.platform.analytics.spotify.service.SpotifyOAuthService;
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
public class SpotifyConnectionServiceImpl implements SpotifyConnectionService {

    private final SpotifyOAuthService spotifyOAuthService;
    private final SpotifyApiClient spotifyApiClient;
    private final StateTokenService stateTokenService;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialAccountMapper socialAccountMapper;
    private final AnalyticsSyncService analyticsSyncService;
    private final SpotifyProperties spotifyProperties;
    private final NotificationService notificationService;

    @Override
    public String getAuthorizationUrl(UUID userId) {
        assertIntegrationEnabled();
        return spotifyOAuthService.buildAuthorizationUrl(userId);
    }

    @Override
    @Transactional
    public SocialAccountResponse completeConnection(String code, String state) {
        assertIntegrationEnabled();
        UUID userId = stateTokenService.verifyAndExtractUserId(state);

        log.info("Completing Spotify OAuth connection for user {}", userId);

        SpotifyTokenResponse tokens = spotifyOAuthService.exchangeCodeForTokens(code);
        SpotifyUserProfileResponse profile = fetchProfile(tokens.accessToken());

        // Spotify's Development Mode (the default access tier, absent a
        // 250k+ MAU Extended Quota application) requires the connecting
        // account to be Premium — enforce this explicitly rather than
        // letting later API calls fail with an opaque 403.
        if (profile.product() != null && !"premium".equalsIgnoreCase(profile.product())) {
            throw new BadRequestException(
                    "Connecting a Spotify account requires Spotify Premium. This account's subscription tier is: "
                            + profile.product());
        }

        String accountId = profile.id();
        String displayName = profile.displayName() != null ? profile.displayName() : "Spotify User";
        String profileImage = (profile.images() != null && !profile.images().isEmpty())
                ? profile.images().get(0).url()
                : null;

        Optional<SocialAccount> existing =
                socialAccountRepository.findByUserIdAndPlatformAndAccountId(userId, Platform.SPOTIFY, accountId);
        boolean isNewConnection = existing.isEmpty();

        // uk_platform_account_id is global (platform + account_id), not
        // scoped to this user - without this check, a different user
        // connecting the same Spotify account would only fail at
        // transaction-commit time, after syncAccount() below has already
        // run as an irreversible side effect.
        if (isNewConnection && socialAccountRepository.existsByPlatformAndAccountId(Platform.SPOTIFY, accountId)) {
            throw new BadRequestException("This Spotify account is already connected to another account.");
        }

        SocialAccount account = existing.orElseGet(() -> SocialAccount.builder()
                        .userId(userId)
                        .platform(Platform.SPOTIFY)
                        .accountId(accountId)
                        .connectedAt(LocalDateTime.now())
                        .build());

        account.setAccountName(displayName);
        account.setUsername(displayName);
        account.setProfileImage(profileImage);
        account.setAccessToken(tokens.accessToken());

        // Spotify usually — but not always — returns a fresh refresh_token
        // on reconnect; keep the previously stored one when it's omitted.
        if (tokens.refreshToken() != null && !tokens.refreshToken().isBlank()) {
            account.setRefreshToken(tokens.refreshToken());
        }

        if (tokens.expiresInSeconds() != null) {
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(tokens.expiresInSeconds()));
        }
        account.setActive(true);

        SocialAccount saved = socialAccountRepository.saveAndFlush(account);
        log.info("Connected/updated real Spotify account {} (spotifyUserId={}) for user {}",
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
                    "Your Spotify account was connected successfully.",
                    Map.of("accountId", account.getId().toString()));
        } catch (Exception ex) {
            log.warn("Failed to send account-connected notification for account {}: {}",
                    account.getId(), ex.getMessage());
        }
    }

    private SpotifyUserProfileResponse fetchProfile(String accessToken) {
        try {
            return spotifyApiClient.getCurrentUserProfile("Bearer " + accessToken);
        } catch (FeignException ex) {
            log.error("Failed to fetch profile during Spotify connect: {}", ex.getMessage());
            throw new ExternalApiException("Failed to fetch Spotify profile", ex);
        }
    }

    private void assertIntegrationEnabled() {
        if (!spotifyProperties.isEnabled()) {
            throw new BadRequestException(
                    "The real Spotify integration is not enabled on this server (spotify.enabled=false). " +
                            "Connected accounts currently use simulated data via MockSocialMediaClient.");
        }
    }
}
