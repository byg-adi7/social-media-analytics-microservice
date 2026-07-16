package com.platform.analytics.client;

import com.platform.analytics.client.youtube.YouTubeDataApiClient;
import com.platform.analytics.client.youtube.dto.GoogleTokenResponse;
import com.platform.analytics.client.youtube.dto.YouTubeChannelListResponse;
import com.platform.analytics.client.youtube.dto.YouTubeSearchListResponse;
import com.platform.analytics.client.youtube.dto.YouTubeVideoListResponse;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.TopContentResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.service.YouTubeOAuthService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Real implementation of {@link SocialMediaClient} for YouTube, backed by
 * the YouTube Data API v3 and the connected creator's own OAuth token.
 * <p>
 * Only created when {@code youtube.enabled=true} (see
 * {@link com.platform.analytics.config.YouTubeProperties}). When disabled —
 * the default, since it requires real Google Cloud OAuth credentials —
 * {@link MockSocialMediaClient} continues to handle YouTube accounts, so
 * the rest of the service (scheduler, calculations, endpoints) works
 * unchanged either way.
 * <p>
 * <b>Known limitation:</b> the YouTube Data API only exposes cumulative
 * channel totals (all-time subscriber/view counts), not day-by-day deltas.
 * True daily analytics would require the separate YouTube Analytics API
 * (a different OAuth scope and endpoint). This client stores each day's
 * cumulative totals as that day's snapshot — the same approach
 * {@link MockSocialMediaClient} uses — which is sufficient for the
 * growth/trend calculations in {@code AnalyticsCalculator}, since those
 * work off day-over-day deltas of stored snapshots.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "youtube", name = "enabled", havingValue = "true")
public class YouTubeSocialMediaClient implements SocialMediaClient {

    private static final String CHANNEL_PART = "snippet,statistics";
    private static final String SEARCH_PART = "snippet";
    private static final String VIDEO_PART = "snippet,statistics";

    private final YouTubeDataApiClient youTubeDataApiClient;
    private final YouTubeOAuthService youTubeOAuthService;
    private final SocialAccountRepository socialAccountRepository;

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.YOUTUBE;
    }

    @Override
    public DailyMetrics fetchDailyMetrics(SocialAccount account, LocalDate date) {
        String bearerToken = "Bearer " + resolveFreshAccessToken(account);

        YouTubeChannelListResponse.Item channel = fetchChannel(bearerToken, account);

        long subscribers = parseLongSafe(channel.statistics() != null ? channel.statistics().subscriberCount() : null);
        long totalViews = parseLongSafe(channel.statistics() != null ? channel.statistics().viewCount() : null);
        long videoCount = parseLongSafe(channel.statistics() != null ? channel.statistics().videoCount() : null);

        // Approximate "today's" engagement using the channel's most recent
        // uploads, since per-day deltas require the YouTube Analytics API.
        List<YouTubeVideoListResponse.Item> recentVideos = fetchRecentVideos(bearerToken, 5);

        long recentLikes = recentVideos.stream()
                .mapToLong(v -> parseLongSafe(v.statistics() != null ? v.statistics().likeCount() : null))
                .sum();
        long recentComments = recentVideos.stream()
                .mapToLong(v -> parseLongSafe(v.statistics() != null ? v.statistics().commentCount() : null))
                .sum();
        long recentViews = recentVideos.stream()
                .mapToLong(v -> parseLongSafe(v.statistics() != null ? v.statistics().viewCount() : null))
                .sum();

        long postsToday = recentVideos.stream()
                .filter(v -> publishedOn(v.snippet() != null ? v.snippet().publishedAt() : null, date))
                .count();

        log.info("Fetched real YouTube channel metrics for account={} channel={} subscribers={} totalViews={}",
                account.getId(), channel.id(), subscribers, totalViews);

        return new DailyMetrics(
                subscribers,          // followers
                0L,                    // following — not applicable to YouTube channels
                totalViews,            // impressions (proxy: cumulative views)
                totalViews,            // reach (proxy: cumulative views; Data API has no separate reach metric)
                0L,                    // profileVisits — not exposed by the Data API
                recentViews > 0 ? recentViews : totalViews, // views (recent-uploads proxy for "today")
                0.0,                    // watchTime — requires YouTube Analytics API, not Data API
                recentLikes,
                recentComments,
                0L,                     // shares — not exposed by the Data API
                0L,                     // saves — not exposed by the Data API
                postsToday
        );
    }

    @Override
    public List<TopContentResponse> fetchTopContent(SocialAccount account, int limit) {
        String bearerToken = "Bearer " + resolveFreshAccessToken(account);

        try {
            YouTubeSearchListResponse.Response searchResponse = youTubeDataApiClient.searchMyVideos(
                    bearerToken, SEARCH_PART, true, "video", "viewCount", Math.min(limit, 25));

            if (searchResponse == null || searchResponse.items() == null || searchResponse.items().isEmpty()) {
                return List.of();
            }

            String videoIds = searchResponse.items().stream()
                    .map(item -> item.id().videoId())
                    .collect(Collectors.joining(","));

            YouTubeVideoListResponse.Response videosResponse = youTubeDataApiClient.getVideoStatistics(
                    bearerToken, VIDEO_PART, videoIds);

            List<TopContentResponse> result = new ArrayList<>();
            if (videosResponse != null && videosResponse.items() != null) {
                for (YouTubeVideoListResponse.Item video : videosResponse.items()) {
                    long views = parseLongSafe(video.statistics() != null ? video.statistics().viewCount() : null);
                    long likes = parseLongSafe(video.statistics() != null ? video.statistics().likeCount() : null);
                    long comments = parseLongSafe(video.statistics() != null ? video.statistics().commentCount() : null);

                    result.add(TopContentResponse.builder()
                            .platform(Platform.YOUTUBE)
                            .title(video.snippet() != null ? video.snippet().title() : "Untitled")
                            .publishedDate(parsePublishedDate(video.snippet() != null ? video.snippet().publishedAt() : null))
                            .views(views)
                            .likes(likes)
                            .comments(comments)
                            .shares(0L)
                            .engagementRate(views > 0
                                    ? Math.round(((likes + comments) * 10000.0 / views)) / 100.0
                                    : 0.0)
                            .build());
                }
            }

            return result.stream()
                    .sorted((a, b) -> Long.compare(b.getViews(), a.getViews()))
                    .limit(limit)
                    .toList();
        } catch (FeignException ex) {
            log.error("YouTube Data API call failed while fetching top content for account={}: {}",
                    account.getId(), ex.getMessage());
            throw new ExternalApiException("Failed to fetch top content from YouTube for account " + account.getId(), ex);
        }
    }

    private YouTubeChannelListResponse.Item fetchChannel(String bearerToken, SocialAccount account) {
        try {
            YouTubeChannelListResponse.Response response =
                    youTubeDataApiClient.getMyChannel(bearerToken, CHANNEL_PART, true);

            if (response == null || response.items() == null || response.items().isEmpty()) {
                throw new ExternalApiException("YouTube API returned no channel for account " + account.getId());
            }
            return response.items().get(0);
        } catch (FeignException ex) {
            log.error("YouTube Data API call failed while fetching channel for account={}: {}",
                    account.getId(), ex.getMessage());
            throw new ExternalApiException("Failed to fetch channel data from YouTube for account " + account.getId(), ex);
        }
    }

    private List<YouTubeVideoListResponse.Item> fetchRecentVideos(String bearerToken, int limit) {
        try {
            YouTubeSearchListResponse.Response searchResponse = youTubeDataApiClient.searchMyVideos(
                    bearerToken, SEARCH_PART, true, "video", "date", limit);

            if (searchResponse == null || searchResponse.items() == null || searchResponse.items().isEmpty()) {
                return List.of();
            }

            String videoIds = searchResponse.items().stream()
                    .map(item -> item.id().videoId())
                    .collect(Collectors.joining(","));

            YouTubeVideoListResponse.Response videosResponse =
                    youTubeDataApiClient.getVideoStatistics(bearerToken, VIDEO_PART, videoIds);

            return videosResponse != null && videosResponse.items() != null ? videosResponse.items() : List.of();
        } catch (FeignException ex) {
            log.warn("Failed to fetch recent videos for daily metrics approximation: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Returns a valid (non-expired) access token for the account, proactively
     * refreshing via the stored refresh token if it has expired or is about
     * to.
     */
    private String resolveFreshAccessToken(SocialAccount account) {
        boolean expired = account.getTokenExpiresAt() != null
                && account.getTokenExpiresAt().isBefore(LocalDateTime.now().plusMinutes(1));

        if (!expired) {
            return account.getAccessToken();
        }

        if (account.getRefreshToken() == null || account.getRefreshToken().isBlank()) {
            throw new ExternalApiException(
                    "YouTube access token has expired and no refresh token is available for account " + account.getId()
                            + " — the user must reconnect their account");
        }

        log.info("Refreshing expired YouTube access token for account={}", account.getId());
        GoogleTokenResponse refreshed = youTubeOAuthService.refreshAccessToken(account.getRefreshToken());

        account.setAccessToken(refreshed.accessToken());
        if (refreshed.expiresInSeconds() != null) {
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(refreshed.expiresInSeconds()));
        }
        socialAccountRepository.save(account);

        return account.getAccessToken();
    }

    private long parseLongSafe(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private boolean publishedOn(String publishedAtIso, LocalDate date) {
        LocalDate published = parsePublishedDate(publishedAtIso);
        return published != null && published.isEqual(date);
    }

    private LocalDate parsePublishedDate(String publishedAtIso) {
        if (publishedAtIso == null || publishedAtIso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(publishedAtIso).toLocalDate();
        } catch (Exception ex) {
            return null;
        }
    }
}
