package com.platform.analytics.instagram;

import com.platform.analytics.client.MockSocialMediaClient;
import com.platform.analytics.client.SocialMediaClient;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.AudienceDemographicsResponse;
import com.platform.analytics.dto.response.TopContentResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.instagram.api.InstagramApiClient;
import com.platform.analytics.instagram.api.dto.InstagramDemographicsInsightsResponse;
import com.platform.analytics.instagram.api.dto.InstagramInsightsResponse;
import com.platform.analytics.instagram.api.dto.InstagramLongLivedTokenResponse;
import com.platform.analytics.instagram.api.dto.InstagramMediaListResponse;
import com.platform.analytics.instagram.api.dto.InstagramProfileResponse;
import com.platform.analytics.instagram.service.InstagramOAuthService;
import com.platform.analytics.repository.SocialAccountRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Real implementation of {@link SocialMediaClient} for Instagram, backed by
 * the Instagram Graph API (Business Login for Instagram flow) and the
 * connected creator's own OAuth token.
 * <p>
 * Only created when {@code instagram.enabled=true} (see
 * {@link InstagramProperties}). When disabled — the default, since it
 * requires a real Meta Developer App and an Instagram Business/Creator
 * account — {@link MockSocialMediaClient} continues to handle Instagram
 * accounts.
 * <p>
 * <b>Known limitations, not bugs:</b>
 * <ul>
 *   <li>{@code impressions} is always {@code 0} — Meta deprecated this
 *       metric for media created after July 2, 2024, with no account-level
 *       replacement (the current API consolidates this under
 *       {@code views}/{@code reach}).</li>
 *   <li>{@code profileVisits} is always {@code 0} at the account-daily
 *       level — the old {@code profile_views} metric was deprecated and its
 *       only living equivalent ({@code profile_visits}) is a per-post
 *       insight, not an account-level one.</li>
 *   <li>{@code watchTime} is always {@code 0.0} — the Graph API does not
 *       expose total watch time at the account level (only a per-Reel
 *       average watch time exists, which isn't meaningfully summable into
 *       a daily total).</li>
 *   <li>{@code posts} reflects the account's all-time {@code media_count},
 *       a cumulative snapshot rather than a true daily delta — the same
 *       approach {@link com.platform.analytics.youtube.YouTubeSocialMediaClient}
 *       uses for the same reason (no day-by-day breakdown API).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "instagram", name = "enabled", havingValue = "true")
public class InstagramSocialMediaClient implements SocialMediaClient {

    private static final String PROFILE_FIELDS =
            "user_id,username,name,biography,followers_count,follows_count,media_count,profile_picture_url";
    private static final String MEDIA_FIELDS =
            "id,caption,media_type,timestamp,permalink,thumbnail_url,like_count,comments_count,view_count,shares_count";
    private static final String ACCOUNT_METRICS = "reach,views,likes,comments,shares,saves";
    private static final String DEMOGRAPHICS_METRIC = "follower_demographics";
    private static final String DEMOGRAPHICS_TIMEFRAME = "this_month";
    private static final String TOTAL_VALUE_METRIC_TYPE = "total_value";
    private static final String DAY_PERIOD = "day";
    private static final long REFRESH_BUFFER_DAYS = 7;

    private final InstagramApiClient instagramApiClient;
    private final InstagramOAuthService instagramOAuthService;
    private final SocialAccountRepository socialAccountRepository;

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.INSTAGRAM;
    }

    @Override
    public DailyMetrics fetchDailyMetrics(SocialAccount account, LocalDate date) {
        String accessToken = resolveFreshAccessToken(account);
        String igUserId = account.getAccountId();

        InstagramProfileResponse profile = fetchProfile(accessToken, account);
        long followers = profile.followersCount() != null ? profile.followersCount() : 0L;
        long following = profile.followsCount() != null ? profile.followsCount() : 0L;
        long mediaCount = profile.mediaCount() != null ? profile.mediaCount() : 0L;

        Map<String, Long> insights = fetchAccountInsights(igUserId, accessToken, account);
        long reach = insights.getOrDefault("reach", 0L);
        long views = insights.getOrDefault("views", 0L);
        long likes = insights.getOrDefault("likes", 0L);
        long comments = insights.getOrDefault("comments", 0L);
        long shares = insights.getOrDefault("shares", 0L);
        long saves = insights.getOrDefault("saves", 0L);

        log.info("Fetched real Instagram account metrics for account={} igUserId={} followers={} reach={}",
                account.getId(), igUserId, followers, reach);

        return new DailyMetrics(
                followers,
                following,
                0L,     // impressions — deprecated by Meta, no account-level replacement
                reach,
                0L,     // profileVisits — no account-level equivalent (see class Javadoc)
                views,
                0.0,    // watchTime — not exposed at account level by the Graph API
                likes,
                comments,
                shares,
                saves,
                mediaCount // posts — cumulative total, same approach as YouTubeSocialMediaClient
        );
    }

    @Override
    public List<TopContentResponse> fetchTopContent(SocialAccount account, int limit) {
        String accessToken = resolveFreshAccessToken(account);
        String igUserId = account.getAccountId();

        try {
            InstagramMediaListResponse response = instagramApiClient.getMedia(
                    igUserId, MEDIA_FIELDS, Math.min(limit, 25), accessToken);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                return List.of();
            }

            return response.data().stream()
                    .map(this::toTopContentResponse)
                    .sorted(Comparator.comparingLong(TopContentResponse::getViews).reversed())
                    .limit(limit)
                    .toList();
        } catch (FeignException ex) {
            log.error("Instagram Graph API call failed while fetching top content for account={}: {}",
                    account.getId(), ex.getMessage());
            throw new ExternalApiException("Failed to fetch top content from Instagram for account " + account.getId(), ex);
        }
    }

    @Override
    public Optional<AudienceDemographicsResponse> fetchAudienceDemographics(SocialAccount account) {
        String accessToken = resolveFreshAccessToken(account);
        String igUserId = account.getAccountId();

        try {
            Map<String, Long> byAge = fetchDemographicBreakdown(igUserId, "age", accessToken);
            Map<String, Long> byGender = fetchDemographicBreakdown(igUserId, "gender", accessToken);
            Map<String, Long> byCity = fetchDemographicBreakdown(igUserId, "city", accessToken);
            Map<String, Long> byCountry = fetchDemographicBreakdown(igUserId, "country", accessToken);

            return Optional.of(AudienceDemographicsResponse.builder()
                    .platform(Platform.INSTAGRAM)
                    .accountId(account.getId())
                    .accountName(account.getAccountName())
                    .byAgeRange(byAge)
                    .byGender(byGender)
                    .byCity(byCity)
                    .byCountry(byCountry)
                    .build());
        } catch (FeignException ex) {
            // Common cause: the account has fewer than 100 followers, Meta's
            // documented minimum for follower_demographics — not a bug, so
            // this degrades gracefully rather than failing the whole sync.
            log.warn("Failed to fetch Instagram audience demographics for account={} (often means <100 followers): {}",
                    account.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private TopContentResponse toTopContentResponse(InstagramMediaListResponse.Item item) {
        long views = item.viewCount() != null ? item.viewCount() : 0L;
        long likes = item.likeCount() != null ? item.likeCount() : 0L;
        long comments = item.commentsCount() != null ? item.commentsCount() : 0L;
        long shares = item.sharesCount() != null ? item.sharesCount() : 0L;

        String title = (item.caption() != null && !item.caption().isBlank())
                ? truncate(item.caption(), 120)
                : "Untitled post";

        return TopContentResponse.builder()
                .platform(Platform.INSTAGRAM)
                .title(title)
                .publishedDate(parseTimestamp(item.timestamp()))
                .views(views)
                .likes(likes)
                .comments(comments)
                .shares(shares)
                .engagementRate(views > 0
                        ? Math.round(((likes + comments + shares) * 10000.0 / views)) / 100.0
                        : 0.0)
                .build();
    }

    private InstagramProfileResponse fetchProfile(String accessToken, SocialAccount account) {
        try {
            return instagramApiClient.getProfile(PROFILE_FIELDS, accessToken);
        } catch (FeignException ex) {
            log.error("Instagram Graph API call failed while fetching profile for account={}: {}",
                    account.getId(), ex.getMessage());
            throw new ExternalApiException("Failed to fetch profile from Instagram for account " + account.getId(), ex);
        }
    }

    private Map<String, Long> fetchAccountInsights(String igUserId, String accessToken, SocialAccount account) {
        try {
            InstagramInsightsResponse response = instagramApiClient.getAccountInsights(
                    igUserId, ACCOUNT_METRICS, DAY_PERIOD, TOTAL_VALUE_METRIC_TYPE, accessToken);

            Map<String, Long> result = new HashMap<>();
            if (response != null && response.data() != null) {
                for (InstagramInsightsResponse.Metric metric : response.data()) {
                    long value = metric.totalValue() != null && metric.totalValue().value() != null
                            ? metric.totalValue().value() : 0L;
                    result.put(metric.name(), value);
                }
            }
            return result;
        } catch (FeignException ex) {
            log.warn("Failed to fetch Instagram account insights for account={}: {}", account.getId(), ex.getMessage());
            return Map.of();
        }
    }

    private Map<String, Long> fetchDemographicBreakdown(String igUserId, String breakdown, String accessToken) {
        InstagramDemographicsInsightsResponse response = instagramApiClient.getDemographics(
                igUserId, DEMOGRAPHICS_METRIC, breakdown, DEMOGRAPHICS_TIMEFRAME, TOTAL_VALUE_METRIC_TYPE, accessToken);

        Map<String, Long> result = new LinkedHashMap<>();
        if (response == null || response.data() == null) {
            return result;
        }
        for (InstagramDemographicsInsightsResponse.Metric metric : response.data()) {
            if (metric.totalValue() == null || metric.totalValue().breakdowns() == null) {
                continue;
            }
            for (InstagramDemographicsInsightsResponse.Breakdown b : metric.totalValue().breakdowns()) {
                if (b.results() == null) {
                    continue;
                }
                for (InstagramDemographicsInsightsResponse.Result r : b.results()) {
                    // dimension_values[0] is always "timeframe"; [1] is the
                    // requested single dimension (age/gender/city/country).
                    if (r.dimensionValues() == null || r.dimensionValues().size() < 2) {
                        continue;
                    }
                    String bucket = r.dimensionValues().get(1);
                    result.merge(bucket, r.value() != null ? r.value() : 0L, Long::sum);
                }
            }
        }
        return result;
    }

    /**
     * Returns a valid access token for the account, proactively refreshing
     * if it's within {@value REFRESH_BUFFER_DAYS} days of expiry.
     * <p>
     * Unlike {@code YouTubeSocialMediaClient}/{@code SpotifySocialMediaClient},
     * this never throws for a "missing refresh token" — Instagram's
     * long-lived token model has no separate refresh token; the access
     * token itself is refreshed in place via
     * {@link InstagramOAuthService#refreshLongLivedToken}.
     */
    private String resolveFreshAccessToken(SocialAccount account) {
        boolean nearingExpiry = account.getTokenExpiresAt() != null
                && account.getTokenExpiresAt().isBefore(LocalDateTime.now().plusDays(REFRESH_BUFFER_DAYS));

        if (!nearingExpiry) {
            return account.getAccessToken();
        }

        log.info("Refreshing Instagram long-lived access token for account={}", account.getId());
        InstagramLongLivedTokenResponse refreshed = instagramOAuthService.refreshLongLivedToken(account.getAccessToken());

        account.setAccessToken(refreshed.accessToken());
        if (refreshed.expiresIn() != null) {
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(refreshed.expiresIn()));
        }
        socialAccountRepository.save(account);

        return account.getAccessToken();
    }

    private String truncate(String text, int maxLength) {
        String singleLine = text.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= maxLength ? singleLine : singleLine.substring(0, maxLength - 1) + "…";
    }

    private LocalDate parseTimestamp(String timestampIso) {
        if (timestampIso == null || timestampIso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestampIso).toLocalDate();
        } catch (Exception ex) {
            return null;
        }
    }
}
