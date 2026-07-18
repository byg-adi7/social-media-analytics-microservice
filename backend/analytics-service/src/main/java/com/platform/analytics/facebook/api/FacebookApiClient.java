package com.platform.analytics.facebook.api;

import com.platform.analytics.facebook.api.dto.FacebookAccountsResponse;
import com.platform.analytics.facebook.api.dto.FacebookBreakdownInsightsResponse;
import com.platform.analytics.facebook.api.dto.FacebookInsightsResponse;
import com.platform.analytics.facebook.api.dto.FacebookPageResponse;
import com.platform.analytics.facebook.api.dto.FacebookPostsResponse;
import com.platform.analytics.facebook.api.dto.FacebookTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for the real Facebook Graph API (read-only). Every resource
 * call authenticates via an {@code access_token} query parameter (not a
 * Bearer header) — Facebook's own examples use this form, and it applies
 * uniformly whether the token is a user token or a Page token.
 * <p>
 * Unlike YouTube/TikTok, Facebook's OAuth token endpoint is a plain
 * {@code GET} with query parameters (no form-urlencoded body), so — unlike
 * those platforms — this client can go straight through Feign for token
 * exchange too, with no need for a raw JDK {@code HttpClient}.
 */
@FeignClient(name = "facebook-graph-api", url = "${facebook.graph-base-url}")
public interface FacebookApiClient {

    /**
     * Exchanges an authorization code for a short-lived user access token.
     */
    @GetMapping("/oauth/access_token")
    FacebookTokenResponse exchangeCodeForToken(
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam("code") String code);

    /**
     * Exchanges a short-lived user access token for a long-lived one
     * (~60 days).
     */
    @GetMapping("/oauth/access_token")
    FacebookTokenResponse exchangeForLongLivedToken(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam("fb_exchange_token") String fbExchangeToken);

    /**
     * Lists the Facebook Pages the given user manages, each with its own
     * Page access token (non-expiring, since it's derived here from a
     * long-lived user token).
     */
    @GetMapping("/{userId}/accounts")
    FacebookAccountsResponse getManagedPages(
            @PathVariable("userId") String userId,
            @RequestParam("access_token") String accessToken);

    /**
     * Fetches a Page's own identity fields.
     */
    @GetMapping("/{pageId}")
    FacebookPageResponse getPage(
            @PathVariable("pageId") String pageId,
            @RequestParam("fields") String fields,
            @RequestParam("access_token") String accessToken);

    /**
     * Fetches scalar (single-number-per-period) Page-level insights
     * metrics, e.g. {@code page_follows}, {@code page_media_view}.
     */
    @GetMapping("/{pageId}/insights")
    FacebookInsightsResponse getPageInsights(
            @PathVariable("pageId") String pageId,
            @RequestParam("metric") String metric,
            @RequestParam("period") String period,
            @RequestParam("access_token") String accessToken);

    /**
     * Fetches breakdown-shaped (map-valued) Page-level insights metrics,
     * e.g. {@code page_actions_post_reactions_total}, {@code page_follows_city}.
     */
    @GetMapping("/{pageId}/insights")
    FacebookBreakdownInsightsResponse getPageBreakdownInsights(
            @PathVariable("pageId") String pageId,
            @RequestParam("metric") String metric,
            @RequestParam("period") String period,
            @RequestParam("access_token") String accessToken);

    /**
     * Fetches the Page's most recent posts, with engagement counts inline.
     */
    @GetMapping("/{pageId}/posts")
    FacebookPostsResponse getPagePosts(
            @PathVariable("pageId") String pageId,
            @RequestParam("fields") String fields,
            @RequestParam("limit") int limit,
            @RequestParam("access_token") String accessToken);

    /**
     * Fetches a single post's view-count insight ({@code post_media_view}).
     */
    @GetMapping("/{postId}/insights")
    FacebookInsightsResponse getPostInsights(
            @PathVariable("postId") String postId,
            @RequestParam("metric") String metric,
            @RequestParam("access_token") String accessToken);
}
