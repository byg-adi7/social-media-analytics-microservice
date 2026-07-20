package com.platform.analytics.tiktok;

import com.platform.analytics.client.MockSocialMediaClient;
import com.platform.analytics.client.SocialMediaClient;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.TopContentResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.tiktok.api.TikTokApiClient;
import com.platform.analytics.tiktok.api.dto.TikTokTokenResponse;
import com.platform.analytics.tiktok.api.dto.TikTokUserInfoResponse;
import com.platform.analytics.tiktok.api.dto.TikTokVideoListRequest;
import com.platform.analytics.tiktok.api.dto.TikTokVideoListResponse;
import com.platform.analytics.tiktok.service.TikTokOAuthService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Real implementation of {@link SocialMediaClient} for TikTok, backed by
 * the TikTok Display API (via Login Kit OAuth) and the connected creator's
 * own access token.
 * <p>
 * Only created when {@code tiktok.enabled=true} (see {@link TikTokProperties}).
 * When disabled — the default, since it requires real TikTok Developer
 * credentials — {@link MockSocialMediaClient} continues to handle TikTok
 * accounts, so the rest of the service (scheduler, calculations, endpoints)
 * works unchanged either way.
 * <p>
 * <b>Known limitations (verified against TikTok's official API docs, not
 * blog posts):</b>
 * <ul>
 *   <li>{@code impressions}, {@code reach}, {@code profileVisits}, and
 *   {@code watchTime} have no equivalent anywhere in the Display API — not
 *   deprecated, never exposed — so they are always {@code 0}.</li>
 *   <li>{@code saves} has no equivalent either and is always {@code 0}.</li>
 *   <li>There is no account-level "total views/comments/shares" field —
 *   only per-video counts exist. {@code views}/{@code comments}/{@code shares}
 *   are therefore approximated by summing those counts across the most
 *   recently fetched page of videos (up to 20), the same spirit as the
 *   Spotify integration's recently-played-window approximation.</li>
 *   <li>{@code likes} is the one account-level total TikTok does expose
 *   (the profile's cumulative {@code likes_count}), so that field is real.</li>
 *   <li>Audience demographics are not available at all for a regular
 *   creator app — TikTok only exposes that data via its separate
 *   Marketing/Ads ("TikTok for Business") API, a different developer
 *   program requiring a business account and ad spend context. This client
 *   does not override {@link SocialMediaClient#fetchAudienceDemographics},
 *   so it correctly falls back to the interface's {@code Optional.empty()}
 *   default.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tiktok", name = "enabled", havingValue = "true")
public class TikTokSocialMediaClient implements SocialMediaClient {

    private static final String PROFILE_FIELDS =
            "open_id,display_name,username,avatar_url,follower_count,following_count,likes_count,video_count";
    private static final String VIDEO_FIELDS =
            "id,create_time,cover_image_url,share_url,video_description,title,like_count,comment_count,share_count,view_count";
    private static final int RECENT_VIDEOS_PAGE_SIZE = 20;

    private final TikTokApiClient tikTokApiClient;
    private final TikTokOAuthService tikTokOAuthService;
    private final SocialAccountRepository socialAccountRepository;

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.TIKTOK;
    }

    @Override
    public DailyMetrics fetchDailyMetrics(SocialAccount account, LocalDate date) {
        String bearerToken = "Bearer " + resolveFreshAccessToken(account);

        TikTokUserInfoResponse.User profile = fetchProfile(bearerToken, account);
        List<TikTokVideoListResponse.Video> recentVideos = fetchRecentVideos(bearerToken, account);

        long followers = orZero(profile.followerCount());
        long following = orZero(profile.followingCount());
        long totalLikes = orZero(profile.likesCount());
        long videoCount = orZero(profile.videoCount());

        long recentViews = recentVideos.stream().mapToLong(v -> orZero(v.viewCount())).sum();
        long recentComments = recentVideos.stream().mapToLong(v -> orZero(v.commentCount())).sum();
        long recentShares = recentVideos.stream().mapToLong(v -> orZero(v.shareCount())).sum();

        long postsToday = recentVideos.stream()
                .filter(v -> createdOn(v.createTime(), date))
                .count();

        log.info("Fetched real TikTok profile metrics for account={} openId={} followers={} videoCount={}",
                account.getId(), profile.openId(), followers, videoCount);

        return new DailyMetrics(
                followers,
                following,
                0L,             // impressions — no account-level equivalent in the Display API
                0L,             // reach — no account-level equivalent in the Display API
                0L,             // profileVisits — not exposed by the Display API
                recentViews,    // views — approximated: summed view_count across the most recent page of videos
                0.0,            // watchTime — not exposed by the Display API
                totalLikes,     // likes — real cumulative total (TikTok's own likes_count)
                recentComments, // comments — approximated: summed comment_count across the most recent page of videos
                recentShares,   // shares — approximated: summed share_count across the most recent page of videos
                0L,             // saves — not exposed by the Display API
                postsToday      // posts published on this specific date, not the account's lifetime video_count
        );
    }

    @Override
    public List<TopContentResponse> fetchTopContent(SocialAccount account, int limit) {
        String bearerToken = "Bearer " + resolveFreshAccessToken(account);

        List<TikTokVideoListResponse.Video> videos = fetchRecentVideos(bearerToken, account);

        return videos.stream()
                .map(video -> {
                    long views = orZero(video.viewCount());
                    long likes = orZero(video.likeCount());
                    long comments = orZero(video.commentCount());
                    long shares = orZero(video.shareCount());
                    String caption = video.videoDescription() != null && !video.videoDescription().isBlank()
                            ? video.videoDescription()
                            : (video.title() != null && !video.title().isBlank() ? video.title() : "Untitled post");

                    return TopContentResponse.builder()
                            .platform(Platform.TIKTOK)
                            .title(caption.length() > 120 ? caption.substring(0, 120) : caption)
                            .publishedDate(createdDate(video.createTime()))
                            .views(views)
                            .likes(likes)
                            .comments(comments)
                            .shares(shares)
                            .engagementRate(views > 0
                                    ? Math.round(((likes + comments + shares) * 10000.0 / views)) / 100.0
                                    : 0.0)
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getViews(), a.getViews()))
                .limit(limit)
                .toList();
    }

    private TikTokUserInfoResponse.User fetchProfile(String bearerToken, SocialAccount account) {
        try {
            TikTokUserInfoResponse response = tikTokApiClient.getUserInfo(bearerToken, PROFILE_FIELDS);

            if (response == null || response.data() == null || response.data().user() == null) {
                throw new ExternalApiException("TikTok API returned no profile for account " + account.getId());
            }
            return response.data().user();
        } catch (FeignException ex) {
            log.error("TikTok Display API call failed while fetching profile for account={}: {}",
                    account.getId(), ex.getMessage());
            throw new ExternalApiException("Failed to fetch profile from TikTok for account " + account.getId(), ex);
        }
    }

    private List<TikTokVideoListResponse.Video> fetchRecentVideos(String bearerToken, SocialAccount account) {
        try {
            TikTokVideoListResponse response = tikTokApiClient.listVideos(
                    bearerToken, VIDEO_FIELDS, new TikTokVideoListRequest(null, RECENT_VIDEOS_PAGE_SIZE));

            if (response == null || response.data() == null || response.data().videos() == null) {
                return List.of();
            }
            return response.data().videos();
        } catch (FeignException ex) {
            log.warn("Failed to fetch recent videos for account={}: {}", account.getId(), ex.getMessage());
            return List.of();
        }
    }

    /**
     * Returns a valid (non-expired) access token for the account, proactively
     * refreshing via the stored refresh token if it has expired or is about
     * to. TikTok access tokens are short-lived (24h), so this is exercised
     * on nearly every sync.
     */
    private String resolveFreshAccessToken(SocialAccount account) {
        boolean expired = account.getTokenExpiresAt() != null
                && account.getTokenExpiresAt().isBefore(LocalDateTime.now().plusMinutes(1));

        if (!expired) {
            return account.getAccessToken();
        }

        if (account.getRefreshToken() == null || account.getRefreshToken().isBlank()) {
            throw new ExternalApiException(
                    "TikTok access token has expired and no refresh token is available for account " + account.getId()
                            + " — the user must reconnect their account");
        }

        log.info("Refreshing expired TikTok access token for account={}", account.getId());
        TikTokTokenResponse refreshed = tikTokOAuthService.refreshAccessToken(account.getRefreshToken());

        account.setAccessToken(refreshed.accessToken());
        // TikTok may rotate the refresh token on every refresh call — always
        // adopt whatever is returned, per the official docs.
        if (refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()) {
            account.setRefreshToken(refreshed.refreshToken());
        }
        if (refreshed.expiresInSeconds() != null) {
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(refreshed.expiresInSeconds()));
        }
        socialAccountRepository.save(account);

        return account.getAccessToken();
    }

    private long orZero(Long value) {
        return value != null ? value : 0L;
    }

    private boolean createdOn(Long createTimeEpochSeconds, LocalDate date) {
        LocalDate created = createdDate(createTimeEpochSeconds);
        return created != null && created.isEqual(date);
    }

    private LocalDate createdDate(Long createTimeEpochSeconds) {
        if (createTimeEpochSeconds == null) {
            return null;
        }
        return Instant.ofEpochSecond(createTimeEpochSeconds).atZone(ZoneOffset.UTC).toLocalDate();
    }
}
