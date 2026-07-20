package com.platform.analytics.spotify;

import com.platform.analytics.client.MockSocialMediaClient;
import com.platform.analytics.client.SocialMediaClient;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.TopContentResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.spotify.api.SpotifyApiClient;
import com.platform.analytics.spotify.api.dto.SpotifyFollowedArtistsResponse;
import com.platform.analytics.spotify.api.dto.SpotifyRecentlyPlayedResponse;
import com.platform.analytics.spotify.api.dto.SpotifyTokenResponse;
import com.platform.analytics.spotify.api.dto.SpotifyTopTracksResponse;
import com.platform.analytics.spotify.api.dto.SpotifyUserProfileResponse;
import com.platform.analytics.spotify.service.SpotifyOAuthService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Real implementation of {@link SocialMediaClient} for Spotify, backed by
 * the Spotify Web API and the connected user's own OAuth token.
 * <p>
 * Only created when {@code spotify.enabled=true} (see
 * {@link SpotifyProperties}). When disabled — the default — {@link
 * MockSocialMediaClient} continues to handle Spotify accounts.
 * <p>
 * <b>Fundamental limitation, not a bug:</b> Spotify has no public API for
 * artist streaming analytics (monthly listeners, stream counts, playlist
 * adds) — that data lives only in the private "Spotify for Artists"
 * dashboard, which has no API, or the "Provider API" restricted to
 * labels/distributors with a direct Spotify distribution deal. This
 * client instead reflects the <em>connected user's own listening
 * activity</em>, which is a genuinely different (and narrower) kind of
 * "analytics" than the audience/reach metrics YouTube/Instagram/Facebook
 * provide:
 * <ul>
 *   <li>{@code followers} — real: the connected user's own profile follower count.</li>
 *   <li>{@code following} — real: count of artists the user follows.</li>
 *   <li>{@code views} / {@code watchTime} — approximate: derived from the
 *       last ≤50 recently-played tracks (the only listening-activity window
 *       Spotify exposes), not a true daily total.</li>
 *   <li>{@code impressions}, {@code reach}, {@code profileVisits},
 *       {@code likes}, {@code comments}, {@code shares}, {@code saves},
 *       {@code posts} — always {@code 0}: there is no Spotify equivalent
 *       for a personal account. Not fabricated, just genuinely absent.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spotify", name = "enabled", havingValue = "true")
public class SpotifySocialMediaClient implements SocialMediaClient {

    private static final String DEFAULT_TIME_RANGE = "medium_term";
    private static final int RECENTLY_PLAYED_WINDOW = 50;

    private final SpotifyApiClient spotifyApiClient;
    private final SpotifyOAuthService spotifyOAuthService;
    private final SocialAccountRepository socialAccountRepository;

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.SPOTIFY;
    }

    @Override
    public DailyMetrics fetchDailyMetrics(SocialAccount account, LocalDate date) {
        String bearerToken = "Bearer " + resolveFreshAccessToken(account);

        SpotifyUserProfileResponse profile = fetchProfile(bearerToken, account);
        long followers = profile.followers() != null ? profile.followers().total() : 0L;

        long following = fetchFollowedArtistsCount(bearerToken, account);

        SpotifyRecentlyPlayedResponse recentlyPlayed = fetchRecentlyPlayed(bearerToken, account);
        List<SpotifyRecentlyPlayedResponse.Item> items = recentlyPlayed.items() != null
                ? recentlyPlayed.items() : List.of();

        long recentPlayCount = items.size();
        double recentListeningMinutes = items.stream()
                .mapToLong(item -> item.track() != null ? item.track().durationMs() : 0L)
                .sum() / 60000.0;

        log.info("Fetched real Spotify listening metrics for account={} spotifyUserId={} followers={} following={}",
                account.getId(), profile.id(), followers, following);

        return new DailyMetrics(
                followers,
                following,
                0L,                                     // impressions — no Spotify equivalent for a personal account
                0L,                                     // reach — no Spotify equivalent
                0L,                                     // profileVisits — no Spotify equivalent
                recentPlayCount,                        // views — recent-plays proxy, not a true daily count
                Math.round(recentListeningMinutes * 100.0) / 100.0, // watchTime — real, but only over the recent-plays window
                0L,                                     // likes — no equivalent concept for personal listening
                0L,                                     // comments — no equivalent concept
                0L,                                     // shares — no equivalent concept
                0L,                                     // saves — no equivalent concept
                0L                                      // posts — no equivalent concept
        );
    }

    @Override
    public List<TopContentResponse> fetchTopContent(SocialAccount account, int limit) {
        String bearerToken = "Bearer " + resolveFreshAccessToken(account);

        try {
            SpotifyTopTracksResponse response = spotifyApiClient.getTopTracks(
                    bearerToken, DEFAULT_TIME_RANGE, Math.min(limit, 50));

            if (response == null || response.items() == null || response.items().isEmpty()) {
                return List.of();
            }

            return response.items().stream()
                    .map(this::toTopContentResponse)
                    .limit(limit)
                    .toList();
        } catch (FeignException ex) {
            log.error("Spotify Web API call failed while fetching top tracks for account={}: {}",
                    account.getId(), ex.getMessage());
            throw new ExternalApiException("Failed to fetch top tracks from Spotify for account " + account.getId(), ex);
        }
    }

    /**
     * Maps a top track to {@link TopContentResponse}.
     * <p>
     * <b>Important caveat:</b> {@code views} holds Spotify's own 0-100
     * "popularity" score here, not a play/stream count — Spotify does not
     * expose per-track play counts via the public API. This is
     * intentionally documented rather than hidden: a Spotify entry's
     * "views" is not comparable in magnitude to a YouTube video's real
     * view count in any cross-platform top-content comparison.
     */
    private TopContentResponse toTopContentResponse(SpotifyTopTracksResponse.Item track) {
        String primaryArtist = (track.artists() != null && !track.artists().isEmpty())
                ? track.artists().get(0).name()
                : null;
        String title = primaryArtist != null ? track.name() + " — " + primaryArtist : track.name();

        return TopContentResponse.builder()
                .platform(Platform.SPOTIFY)
                .title(title)
                .publishedDate(parseReleaseDate(track.album() != null ? track.album().releaseDate() : null))
                .views(track.popularity() != null ? track.popularity() : 0)
                .likes(0L)
                .comments(0L)
                .shares(0L)
                .engagementRate(0.0)
                .build();
    }

    private SpotifyUserProfileResponse fetchProfile(String bearerToken, SocialAccount account) {
        try {
            return spotifyApiClient.getCurrentUserProfile(bearerToken);
        } catch (FeignException ex) {
            log.error("Spotify Web API call failed while fetching profile for account={}: {}",
                    account.getId(), ex.getMessage());
            throw new ExternalApiException("Failed to fetch profile from Spotify for account " + account.getId(), ex);
        }
    }

    private long fetchFollowedArtistsCount(String bearerToken, SocialAccount account) {
        try {
            SpotifyFollowedArtistsResponse response = spotifyApiClient.getFollowedArtists(bearerToken, "artist");
            return response.artists() != null ? response.artists().total() : 0L;
        } catch (FeignException ex) {
            log.warn("Failed to fetch followed-artist count for account={}: {}", account.getId(), ex.getMessage());
            return 0L;
        }
    }

    private SpotifyRecentlyPlayedResponse fetchRecentlyPlayed(String bearerToken, SocialAccount account) {
        try {
            return spotifyApiClient.getRecentlyPlayed(bearerToken, RECENTLY_PLAYED_WINDOW);
        } catch (FeignException ex) {
            log.warn("Failed to fetch recently-played tracks for account={}: {}", account.getId(), ex.getMessage());
            return new SpotifyRecentlyPlayedResponse(List.of());
        }
    }

    /**
     * Returns a valid (non-expired) access token for the account,
     * proactively refreshing via the stored refresh token if it has
     * expired or is about to.
     */
    private String resolveFreshAccessToken(SocialAccount account) {
        boolean expired = account.getTokenExpiresAt() != null
                && account.getTokenExpiresAt().isBefore(LocalDateTime.now().plusMinutes(1));

        if (!expired) {
            return account.getAccessToken();
        }

        if (account.getRefreshToken() == null || account.getRefreshToken().isBlank()) {
            throw new ExternalApiException(
                    "Spotify access token has expired and no refresh token is available for account " + account.getId()
                            + " — the user must reconnect their account");
        }

        log.info("Refreshing expired Spotify access token for account={}", account.getId());
        SpotifyTokenResponse refreshed = spotifyOAuthService.refreshAccessToken(account.getRefreshToken());

        account.setAccessToken(refreshed.accessToken());
        if (refreshed.expiresInSeconds() != null) {
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(refreshed.expiresInSeconds()));
        }
        if (refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()) {
            account.setRefreshToken(refreshed.refreshToken());
        }
        socialAccountRepository.save(account);

        return account.getAccessToken();
    }

    /**
     * Spotify's {@code release_date} precision varies by album — it may be
     * a full {@code yyyy-MM-dd}, a {@code yyyy-MM}, or just {@code yyyy} —
     * so this falls back progressively rather than assuming full-date
     * precision.
     */
    private LocalDate parseReleaseDate(String releaseDate) {
        if (releaseDate == null || releaseDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(releaseDate, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            try {
                if (releaseDate.length() == 7) { // yyyy-MM
                    return LocalDate.parse(releaseDate + "-01");
                }
                if (releaseDate.length() == 4) { // yyyy
                    return LocalDate.of(Integer.parseInt(releaseDate), 1, 1);
                }
            } catch (Exception ignored) {
                // fall through to null
            }
            return null;
        }
    }
}
