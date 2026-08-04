package com.platform.analytics.oauth;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.ConflictException;
import com.platform.analytics.facebook.FacebookProperties;
import com.platform.analytics.facebook.api.FacebookApiClient;
import com.platform.analytics.facebook.api.dto.FacebookAccountsResponse;
import com.platform.analytics.facebook.api.dto.FacebookPageResponse;
import com.platform.analytics.facebook.api.dto.FacebookTokenResponse;
import com.platform.analytics.facebook.service.FacebookOAuthService;
import com.platform.analytics.facebook.service.impl.FacebookConnectionServiceImpl;
import com.platform.analytics.instagram.InstagramProperties;
import com.platform.analytics.instagram.api.InstagramApiClient;
import com.platform.analytics.instagram.api.dto.InstagramLongLivedTokenResponse;
import com.platform.analytics.instagram.api.dto.InstagramProfileResponse;
import com.platform.analytics.instagram.api.dto.InstagramShortLivedTokenResponse;
import com.platform.analytics.instagram.service.InstagramOAuthService;
import com.platform.analytics.instagram.service.impl.InstagramConnectionServiceImpl;
import com.platform.analytics.mapper.SocialAccountMapper;
import com.platform.analytics.repository.AnalyticsRepository;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.security.StateTokenService;
import com.platform.analytics.service.AnalyticsSyncService;
import com.platform.analytics.spotify.SpotifyProperties;
import com.platform.analytics.spotify.api.SpotifyApiClient;
import com.platform.analytics.spotify.api.dto.SpotifyTokenResponse;
import com.platform.analytics.spotify.api.dto.SpotifyUserProfileResponse;
import com.platform.analytics.spotify.service.SpotifyOAuthService;
import com.platform.analytics.spotify.service.impl.SpotifyConnectionServiceImpl;
import com.platform.analytics.tiktok.TikTokProperties;
import com.platform.analytics.tiktok.api.TikTokApiClient;
import com.platform.analytics.tiktok.api.dto.TikTokTokenResponse;
import com.platform.analytics.tiktok.api.dto.TikTokUserInfoResponse;
import com.platform.analytics.tiktok.service.TikTokOAuthService;
import com.platform.analytics.tiktok.service.impl.TikTokConnectionServiceImpl;
import com.platform.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthConnectionConflictRegressionTest {

    private static final String CODE = "oauth-code";
    private static final String STATE = "oauth-state";

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
    private NotificationService notificationService;

    @Mock
    private SpotifyOAuthService spotifyOAuthService;

    @Mock
    private SpotifyApiClient spotifyApiClient;

    @Mock
    private SpotifyProperties spotifyProperties;

    @Mock
    private InstagramOAuthService instagramOAuthService;

    @Mock
    private InstagramApiClient instagramApiClient;

    @Mock
    private InstagramProperties instagramProperties;

    @Mock
    private TikTokOAuthService tikTokOAuthService;

    @Mock
    private TikTokApiClient tikTokApiClient;

    @Mock
    private TikTokProperties tikTokProperties;

    @Mock
    private FacebookOAuthService facebookOAuthService;

    @Mock
    private FacebookApiClient facebookApiClient;

    @Mock
    private FacebookProperties facebookProperties;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        when(stateTokenService.verifyAndExtractUserId(STATE)).thenReturn(userId);
    }

    @Test
    void spotify_activeDifferentUserAccount_throwsConflictBeforeSave() {
        when(spotifyProperties.isEnabled()).thenReturn(true);
        when(spotifyOAuthService.exchangeCodeForTokens(CODE))
                .thenReturn(new SpotifyTokenResponse("spotify-access", "spotify-refresh", 3600L, "scope", "Bearer"));
        when(spotifyApiClient.getCurrentUserProfile("Bearer spotify-access"))
                .thenReturn(new SpotifyUserProfileResponse("spotify-user-id", "Spotify User", null,
                        "premium", null, List.of()));
        when(socialAccountRepository.findByPlatformAndAccountId(Platform.SPOTIFY, "spotify-user-id"))
                .thenReturn(Optional.of(activeAccount(Platform.SPOTIFY, "spotify-user-id")));

        SpotifyConnectionServiceImpl service = new SpotifyConnectionServiceImpl(
                spotifyOAuthService,
                spotifyApiClient,
                stateTokenService,
                socialAccountRepository,
                analyticsRepository,
                socialAccountMapper,
                analyticsSyncService,
                spotifyProperties,
                notificationService);

        assertThatThrownBy(() -> service.completeConnection(CODE, STATE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already connected");

        verifyNoPersistenceSideEffects();
    }

    @Test
    void instagram_activeDifferentUserAccount_throwsConflictBeforeSave() {
        when(instagramProperties.isEnabled()).thenReturn(true);
        when(instagramOAuthService.exchangeCodeForShortLivedToken(CODE))
                .thenReturn(new InstagramShortLivedTokenResponse(List.of(
                        new InstagramShortLivedTokenResponse.Entry("instagram-short", "instagram-user-id", "permissions"))));
        when(instagramOAuthService.exchangeForLongLivedToken("instagram-short"))
                .thenReturn(new InstagramLongLivedTokenResponse("instagram-long", "Bearer", 5_184_000L));
        when(instagramApiClient.getProfile(anyString(), eq("instagram-long")))
                .thenReturn(new InstagramProfileResponse("instagram-user-id", "iguser", "Instagram User",
                        null, 100L, 50L, 12L, null));
        when(socialAccountRepository.findByPlatformAndAccountId(Platform.INSTAGRAM, "instagram-user-id"))
                .thenReturn(Optional.of(activeAccount(Platform.INSTAGRAM, "instagram-user-id")));

        InstagramConnectionServiceImpl service = new InstagramConnectionServiceImpl(
                instagramOAuthService,
                instagramApiClient,
                stateTokenService,
                socialAccountRepository,
                analyticsRepository,
                socialAccountMapper,
                analyticsSyncService,
                instagramProperties,
                notificationService);

        assertThatThrownBy(() -> service.completeConnection(CODE, STATE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already connected");

        verifyNoPersistenceSideEffects();
    }

    @Test
    void tiktok_activeDifferentUserAccount_throwsConflictBeforeSave() {
        when(tikTokProperties.isEnabled()).thenReturn(true);
        when(tikTokOAuthService.exchangeCodeForTokens(CODE))
                .thenReturn(new TikTokTokenResponse("tiktok-access", 3600L, "tiktok-open-id",
                        "tiktok-refresh", 86_400L, "scope", "Bearer"));
        when(tikTokApiClient.getUserInfo(eq("Bearer tiktok-access"), anyString()))
                .thenReturn(new TikTokUserInfoResponse(
                        new TikTokUserInfoResponse.Data(new TikTokUserInfoResponse.User(
                                "tiktok-open-id", "TikTok User", "ttuser", null, 10L, 5L, 100L, 3L)),
                        null));
        when(socialAccountRepository.findByPlatformAndAccountId(Platform.TIKTOK, "tiktok-open-id"))
                .thenReturn(Optional.of(activeAccount(Platform.TIKTOK, "tiktok-open-id")));

        TikTokConnectionServiceImpl service = new TikTokConnectionServiceImpl(
                tikTokOAuthService,
                tikTokApiClient,
                stateTokenService,
                socialAccountRepository,
                analyticsRepository,
                socialAccountMapper,
                analyticsSyncService,
                tikTokProperties,
                notificationService);

        assertThatThrownBy(() -> service.completeConnection(CODE, STATE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already connected");

        verifyNoPersistenceSideEffects();
    }

    @Test
    void facebook_activeDifferentUserPage_throwsConflictBeforeSave() {
        when(facebookProperties.isEnabled()).thenReturn(true);
        when(facebookOAuthService.exchangeCodeForToken(CODE))
                .thenReturn(new FacebookTokenResponse("facebook-short", "Bearer", 3600L));
        when(facebookOAuthService.exchangeForLongLivedToken("facebook-short"))
                .thenReturn(new FacebookTokenResponse("facebook-long", "Bearer", 5_184_000L));
        when(facebookApiClient.getManagedPages("me", "facebook-long"))
                .thenReturn(new FacebookAccountsResponse(List.of(
                        new FacebookAccountsResponse.Page("facebook-page-id", "Facebook Page", "Creator", "facebook-page-token"))));
        when(facebookApiClient.getPage(eq("facebook-page-id"), anyString(), eq("facebook-page-token")))
                .thenReturn(new FacebookPageResponse("facebook-page-id", "Facebook Page", "Creator",
                        100L, 90L, null));
        when(socialAccountRepository.findByPlatformAndAccountId(Platform.FACEBOOK, "facebook-page-id"))
                .thenReturn(Optional.of(activeAccount(Platform.FACEBOOK, "facebook-page-id")));

        FacebookConnectionServiceImpl service = new FacebookConnectionServiceImpl(
                facebookOAuthService,
                facebookApiClient,
                stateTokenService,
                socialAccountRepository,
                analyticsRepository,
                socialAccountMapper,
                analyticsSyncService,
                facebookProperties,
                notificationService);

        assertThatThrownBy(() -> service.completeConnection(CODE, STATE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already connected");

        verifyNoPersistenceSideEffects();
    }

    private SocialAccount activeAccount(Platform platform, String accountId) {
        return SocialAccount.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .platform(platform)
                .accountId(accountId)
                .connectedAt(LocalDateTime.now().minusDays(1))
                .active(true)
                .build();
    }

    private void verifyNoPersistenceSideEffects() {
        verify(socialAccountRepository, never()).save(any());
        verify(socialAccountRepository, never()).delete(any());
        verify(analyticsRepository, never()).deleteBySocialAccountId(any());
        verify(analyticsSyncService, never()).syncAccount(any());
    }
}
