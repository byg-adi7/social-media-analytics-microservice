package com.platform.analytics.spotify.service.impl;

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
import com.platform.analytics.spotify.SpotifyProperties;
import com.platform.analytics.spotify.api.SpotifyApiClient;
import com.platform.analytics.spotify.api.dto.SpotifyTokenResponse;
import com.platform.analytics.spotify.api.dto.SpotifyUserProfileResponse;
import com.platform.analytics.spotify.service.SpotifyConnectionService;
import com.platform.analytics.spotify.service.SpotifyOAuthService;
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
public class SpotifyConnectionServiceImpl implements SpotifyConnectionService {

    private final SpotifyOAuthService spotifyOAuthService;
    private final SpotifyApiClient spotifyApiClient;
    private final StateTokenService stateTokenService;
    private final SocialAccountRepository socialAccountRepository;
    private final AnalyticsRepository analyticsRepository;
    private final SocialAccountMapper socialAccountMapper;
    private final AnalyticsSyncService analyticsSyncService;
    private final SpotifyProperties spotifyProperties;

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

        SocialAccount account = resolveAccountForConnection(userId, accountId);

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
        account.setConnectionType(AccountConnectionType.OAUTH);
        account.setActive(true);

        SocialAccount saved = socialAccountRepository.save(account);
        log.info("Connected/updated real Spotify account {} (spotifyUserId={}) for user {}",
                saved.getId(), accountId, userId);

        analyticsSyncService.syncAccount(saved);

        return socialAccountMapper.toResponse(saved);
    }

    private SocialAccount resolveAccountForConnection(UUID userId, String accountId) {
        Optional<SocialAccount> existing =
                socialAccountRepository.findByPlatformAndAccountId(Platform.SPOTIFY, accountId);

        if (existing.isEmpty()) {
            return SocialAccount.builder()
                    .userId(userId)
                    .platform(Platform.SPOTIFY)
                    .accountId(accountId)
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
                    .platform(Platform.SPOTIFY)
                    .accountId(accountId)
                    .connectedAt(LocalDateTime.now())
                    .build();
        }

        throw new ConflictException("This Spotify account is already connected to another user");
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
