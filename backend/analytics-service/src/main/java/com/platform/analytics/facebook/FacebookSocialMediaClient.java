package com.platform.analytics.facebook;

import com.platform.analytics.client.MockSocialMediaClient;
import com.platform.analytics.client.SocialMediaClient;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.AudienceDemographicsResponse;
import com.platform.analytics.dto.response.TopContentResponse;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.facebook.api.FacebookApiClient;
import com.platform.analytics.facebook.api.dto.FacebookBreakdownInsightsResponse;
import com.platform.analytics.facebook.api.dto.FacebookInsightsResponse;
import com.platform.analytics.facebook.api.dto.FacebookPageResponse;
import com.platform.analytics.facebook.api.dto.FacebookPostsResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Real implementation of {@link SocialMediaClient} for Facebook, backed by
 * the Graph API's Page endpoints and the connected Page's own (non-expiring)
 * Page access token.
 * <p>
 * Only created when {@code facebook.enabled=true} (see {@link FacebookProperties}).
 * When disabled — the default, since it requires a real Meta Developer app
 * — {@link MockSocialMediaClient} continues to handle Facebook accounts, so
 * the rest of the service (scheduler, calculations, endpoints) works
 * unchanged either way.
 * <p>
 * <b>Known limitations (verified against Meta's official Graph API docs,
 * including their 2025 metric-deprecation waves — not blog posts):</b>
 * <ul>
 *   <li>{@code page_fans} and {@code page_impressions} are already
 *   deprecated. {@code followers} uses the Page node's {@code followers_count}
 *   field instead (the forward-compatible replacement — on "New Page
 *   Experience" Pages, the older {@code fan_count} field silently returns
 *   the same value anyway).</li>
 *   <li>{@code impressions} is always {@code 0} — the term itself was
 *   retired in favor of {@code page_media_view}, which this client reports
 *   under {@code views} instead; duplicating the same number under both
 *   fields would be misleading, not additive.</li>
 *   <li>{@code profileVisits}, {@code watchTime}, and {@code saves} have no
 *   confirmed Facebook Page equivalent and are always {@code 0}.</li>
 *   <li>There is no account-level total for comments/shares, only
 *   per-post counts, so {@code comments}/{@code shares} are approximated by
 *   summing those counts across the most recent page of posts — the same
 *   approach used for TikTok.</li>
 *   <li>{@code likes} is real: the sum of {@code page_actions_post_reactions_total},
 *   a breakdown metric of that day's reactions by type (like/love/wow/etc).</li>
 *   <li>Top content requires one extra Graph API call per candidate post
 *   ({@code post_media_view}) to get a real view count, since — unlike
 *   YouTube/Instagram/TikTok — Facebook doesn't return per-post view counts
 *   in the same call that lists posts.</li>
 *   <li>Audience demographics are <b>partial</b>: city/country breakdowns
 *   are real ({@code page_follows_city}/{@code page_follows_country}), but
 *   age/gender are not available at all — Meta's docs state that any app
 *   connection made after March 14, 2024 (which includes this integration)
 *   has no access to Page audience age/gender data, full stop. {@code byAgeRange}/
 *   {@code byGender} are therefore always empty maps, not fabricated.</li>
 *   <li>Page access tokens derived from a long-lived user token do not
 *   expire under normal conditions, so — unlike every other platform here —
 *   there is no proactive refresh step. If Facebook ever invalidates the
 *   token (revoked access, password change), calls fail and the user must
 *   reconnect; this client does not attempt to silently recover from that.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "facebook", name = "enabled", havingValue = "true")
public class FacebookSocialMediaClient implements SocialMediaClient {

    private static final String PAGE_FIELDS = "id,name,followers_count,fan_count";
    private static final String POST_FIELDS =
            "id,message,created_time,full_picture,permalink_url,likes.summary(true),comments.summary(true),shares";
    private static final int RECENT_POSTS_LIMIT = 20;
    private static final int TOP_CONTENT_CANDIDATE_LIMIT = 15;

    private static final String VIEWS_METRIC = "page_media_view";
    private static final String REACH_METRIC = "page_total_media_view_unique";
    private static final String REACTIONS_METRIC = "page_actions_post_reactions_total";
    private static final String CITY_METRIC = "page_follows_city";
    private static final String COUNTRY_METRIC = "page_follows_country";
    private static final String POST_VIEWS_METRIC = "post_media_view";
    private static final String DAY_PERIOD = "day";
    private static final String LIFETIME_PERIOD = "lifetime";

    private static final DateTimeFormatter CREATED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final FacebookApiClient facebookApiClient;

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.FACEBOOK;
    }

    @Override
    public DailyMetrics fetchDailyMetrics(SocialAccount account, LocalDate date) {
        String pageId = account.getAccountId();
        String accessToken = resolveAccessToken(account);

        FacebookPageResponse page = fetchPage(pageId, accessToken, account);
        long followers = page.followersCount() != null ? page.followersCount() : orZero(page.fanCount());

        long views = fetchScalarMetric(pageId, VIEWS_METRIC, date, accessToken, account);
        long reach = fetchScalarMetric(pageId, REACH_METRIC, date, accessToken, account);

        Map<String, Long> reactions = fetchBreakdownMetric(pageId, REACTIONS_METRIC, date, accessToken, account);
        long totalLikes = reactions.values().stream().mapToLong(Long::longValue).sum();

        List<FacebookPostsResponse.Post> recentPosts = fetchRecentPosts(pageId, accessToken, RECENT_POSTS_LIMIT, account);

        long recentComments = recentPosts.stream()
                .mapToLong(p -> p.comments() != null && p.comments().summary() != null
                        ? orZero(p.comments().summary().totalCount()) : 0L)
                .sum();
        long recentShares = recentPosts.stream()
                .mapToLong(p -> p.shares() != null ? orZero(p.shares().count()) : 0L)
                .sum();
        long postsToday = recentPosts.stream()
                .filter(p -> createdOn(p.createdTime(), date))
                .count();

        log.info("Fetched real Facebook Page metrics for account={} pageId={} followers={} views={}",
                account.getId(), pageId, followers, views);

        return new DailyMetrics(
                followers,
                0L,             // following — not applicable to a Facebook Page
                0L,             // impressions — retired term; see 'views' (page_media_view) instead
                reach,          // page_total_media_view_unique — real replacement for the deprecated page_impressions_unique
                0L,             // profileVisits — no confirmed Facebook Page equivalent
                views,          // page_media_view — real
                0.0,            // watchTime — no confirmed account-level metric
                totalLikes,     // real — summed page_actions_post_reactions_total
                recentComments, // approximated: summed across the most recent page of posts
                recentShares,   // approximated: summed across the most recent page of posts
                0L,             // saves — no Facebook equivalent
                postsToday
        );
    }

    @Override
    public List<TopContentResponse> fetchTopContent(SocialAccount account, int limit) {
        String pageId = account.getAccountId();
        String accessToken = resolveAccessToken(account);

        List<FacebookPostsResponse.Post> candidates = fetchRecentPosts(pageId, accessToken, TOP_CONTENT_CANDIDATE_LIMIT, account);

        return candidates.stream()
                .map(post -> toTopContentResponse(post, accessToken, account))
                .sorted((a, b) -> Long.compare(b.getViews(), a.getViews()))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<AudienceDemographicsResponse> fetchAudienceDemographics(SocialAccount account) {
        String pageId = account.getAccountId();
        String accessToken = resolveAccessToken(account);

        try {
            // Not tied to a specific sync date - reports the current
            // breakdown as of today.
            LocalDate today = LocalDate.now();
            Map<String, Long> byCity = fetchBreakdownMetricOrEmpty(pageId, CITY_METRIC, today, accessToken, account);
            Map<String, Long> byCountry = fetchBreakdownMetricOrEmpty(pageId, COUNTRY_METRIC, today, accessToken, account);

            return Optional.of(AudienceDemographicsResponse.builder()
                    .platform(Platform.FACEBOOK)
                    .accountId(account.getId())
                    .accountName(account.getAccountName())
                    // Meta blocks Page audience age/gender data entirely for
                    // any app connection made after March 14, 2024 — not
                    // fabricated, genuinely unavailable to this integration.
                    .byAgeRange(Map.of())
                    .byGender(Map.of())
                    .byCity(byCity)
                    .byCountry(byCountry)
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to fetch Facebook audience demographics for account={}: {}",
                    account.getId(), ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private TopContentResponse toTopContentResponse(FacebookPostsResponse.Post post, String accessToken, SocialAccount account) {
        long likes = post.likes() != null && post.likes().summary() != null ? orZero(post.likes().summary().totalCount()) : 0L;
        long comments = post.comments() != null && post.comments().summary() != null ? orZero(post.comments().summary().totalCount()) : 0L;
        long shares = post.shares() != null ? orZero(post.shares().count()) : 0L;
        long views = fetchPostViews(post.id(), accessToken, account);

        String caption = post.message() != null && !post.message().isBlank() ? post.message() : "Untitled post";

        return TopContentResponse.builder()
                .platform(Platform.FACEBOOK)
                .title(caption.length() > 120 ? caption.substring(0, 120) : caption)
                .publishedDate(parseCreatedDate(post.createdTime()))
                .views(views)
                .likes(likes)
                .comments(comments)
                .shares(shares)
                .engagementRate(views > 0
                        ? Math.round(((likes + comments + shares) * 10000.0 / views)) / 100.0
                        : 0.0)
                .build();
    }

    private FacebookPageResponse fetchPage(String pageId, String accessToken, SocialAccount account) {
        try {
            return facebookApiClient.getPage(pageId, PAGE_FIELDS, accessToken);
        } catch (FeignException ex) {
            log.error("Facebook Graph API call failed while fetching Page for account={}: HTTP {}", account.getId(), ex.status());
            throw new ExternalApiException("Failed to fetch Page data from Facebook for account " + account.getId(), ex);
        }
    }

    private long fetchScalarMetric(String pageId, String metric, LocalDate date, String accessToken, SocialAccount account) {
        try {
            FacebookInsightsResponse response = facebookApiClient.getPageInsights(
                    pageId, metric, DAY_PERIOD, date.toString(), date.plusDays(1).toString(), accessToken);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                return 0L;
            }
            List<FacebookInsightsResponse.ValueEntry> values = response.data().get(0).values();
            FacebookInsightsResponse.ValueEntry selected = selectValueForDate(values, date, FacebookInsightsResponse.ValueEntry::endTime);
            return selected != null ? orZero(selected.value()) : 0L;
        } catch (FeignException ex) {
            log.error("Facebook Graph API call failed while fetching metric={} for account={}: HTTP {}",
                    metric, account.getId(), ex.status());
            throw new ExternalApiException("Failed to fetch " + metric + " from Facebook for account " + account.getId(), ex);
        }
    }

    private Map<String, Long> fetchBreakdownMetric(String pageId, String metric, LocalDate date, String accessToken, SocialAccount account) {
        try {
            FacebookBreakdownInsightsResponse response = facebookApiClient.getPageBreakdownInsights(
                    pageId, metric, DAY_PERIOD, date.toString(), date.plusDays(1).toString(), accessToken);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                return Map.of();
            }
            List<FacebookBreakdownInsightsResponse.ValueEntry> values = response.data().get(0).values();
            FacebookBreakdownInsightsResponse.ValueEntry selected = selectValueForDate(values, date, FacebookBreakdownInsightsResponse.ValueEntry::endTime);
            return selected != null && selected.value() != null ? selected.value() : Map.of();
        } catch (FeignException ex) {
            log.error("Facebook Graph API call failed while fetching metric={} for account={}: HTTP {}",
                    metric, account.getId(), ex.status());
            throw new ExternalApiException("Failed to fetch " + metric + " from Facebook for account " + account.getId(), ex);
        }
    }

    private Map<String, Long> fetchBreakdownMetricOrEmpty(String pageId, String metric, LocalDate date, String accessToken, SocialAccount account) {
        try {
            return fetchBreakdownMetric(pageId, metric, date, accessToken, account);
        } catch (ExternalApiException ex) {
            log.warn("Facebook metric={} unavailable for account={}, defaulting to empty", metric, account.getId());
            return Map.of();
        }
    }

    private List<FacebookPostsResponse.Post> fetchRecentPosts(String pageId, String accessToken, int limit, SocialAccount account) {
        try {
            FacebookPostsResponse response = facebookApiClient.getPagePosts(pageId, POST_FIELDS, limit, accessToken);
            return response != null && response.data() != null ? response.data() : List.of();
        } catch (FeignException ex) {
            log.warn("Failed to fetch recent posts for account={}: HTTP {}", account.getId(), ex.status());
            return List.of();
        }
    }

    private long fetchPostViews(String postId, String accessToken, SocialAccount account) {
        try {
            // "lifetime" is required for this endpoint and, unlike the
            // day-scoped Page metrics above, is expected to return a single
            // cumulative value rather than a per-day series - no
            // date-matching needed, just take whatever comes back.
            FacebookInsightsResponse response = facebookApiClient.getPostInsights(postId, POST_VIEWS_METRIC, LIFETIME_PERIOD, accessToken);
            if (response == null || response.data() == null || response.data().isEmpty()) {
                return 0L;
            }
            List<FacebookInsightsResponse.ValueEntry> values = response.data().get(0).values();
            if (values == null || values.isEmpty()) {
                return 0L;
            }
            return orZero(values.get(values.size() - 1).value());
        } catch (FeignException ex) {
            log.warn("Failed to fetch view count for post={} account={}: HTTP {}", postId, account.getId(), ex.status());
            return 0L;
        }
    }

    /**
     * Picks the value entry whose end_time falls on the requested date,
     * rather than trusting a specific array position/order (never
     * confirmed by Meta's docs to be guaranteed ascending or descending).
     * Falls back to the last entry if none match exactly - e.g. if the API
     * still returns exactly one value despite the since/until scoping
     * above, its end_time should already equal the requested date, but this
     * degrades safely instead of returning nothing if that assumption
     * doesn't hold in some edge case.
     */
    private <T> T selectValueForDate(List<T> values, LocalDate date, java.util.function.Function<T, String> endTimeExtractor) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .filter(v -> date.equals(parseEndTimeDate(endTimeExtractor.apply(v))))
                .findFirst()
                .orElseGet(() -> values.get(values.size() - 1));
    }

    private LocalDate parseEndTimeDate(String endTimeIso) {
        if (endTimeIso == null || endTimeIso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(endTimeIso, CREATED_TIME_FORMAT).toLocalDate();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Facebook Page access tokens derived from a long-lived user token do
     * not expire under normal conditions, so — unlike every other platform
     * in this service — there is no {@code tokenExpiresAt} check or
     * proactive refresh here. If the token has been invalidated (revoked
     * access, password change), the Graph API call itself fails and
     * surfaces as an {@link ExternalApiException}; the user must reconnect.
     */
    private String resolveAccessToken(SocialAccount account) {
        if (account.getAccessToken() == null || account.getAccessToken().isBlank()) {
            throw new ExternalApiException(
                    "No Facebook Page access token stored for account " + account.getId()
                            + " — the user must reconnect their account");
        }
        return account.getAccessToken();
    }

    private long orZero(Long value) {
        return value != null ? value : 0L;
    }

    private boolean createdOn(String createdTimeIso, LocalDate date) {
        LocalDate created = parseCreatedDate(createdTimeIso);
        return created != null && created.isEqual(date);
    }

    private LocalDate parseCreatedDate(String createdTimeIso) {
        if (createdTimeIso == null || createdTimeIso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(createdTimeIso, CREATED_TIME_FORMAT).toLocalDate();
        } catch (Exception ex) {
            return null;
        }
    }
}
