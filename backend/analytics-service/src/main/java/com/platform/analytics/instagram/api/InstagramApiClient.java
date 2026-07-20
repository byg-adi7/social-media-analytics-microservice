package com.platform.analytics.instagram.api;

import com.platform.analytics.instagram.api.dto.InstagramDemographicsInsightsResponse;
import com.platform.analytics.instagram.api.dto.InstagramInsightsResponse;
import com.platform.analytics.instagram.api.dto.InstagramLongLivedTokenResponse;
import com.platform.analytics.instagram.api.dto.InstagramMediaListResponse;
import com.platform.analytics.instagram.api.dto.InstagramProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for the real Instagram Graph API (Business Login for
 * Instagram flow, host {@code graph.instagram.com}).
 * <p>
 * Unlike YouTube (Google) and Spotify, which authenticate outbound calls
 * via a Bearer {@code Authorization} header, Instagram's documented calling
 * convention passes the access token as an {@code access_token} query
 * parameter on every request — verified against Meta's "Get Started" guide
 * example for this login flow, not assumed.
 * <p>
 * The {@code /access_token} and {@code /refresh_access_token} endpoints are
 * unversioned; the resource endpoints ({@code /me}, {@code /{id}/media},
 * {@code /{id}/insights}) are pinned to API version {@code v25.0}, matching
 * the version shown in Meta's current documentation examples.
 */
@FeignClient(name = "instagram-graph-api", url = "${instagram.graph-base-url}")
public interface InstagramApiClient {

    /**
     * Exchanges a short-lived access token (from the initial code exchange)
     * for a 60-day long-lived token.
     */
    @GetMapping("/access_token")
    InstagramLongLivedTokenResponse exchangeForLongLivedToken(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam("access_token") String shortLivedAccessToken);

    /**
     * Refreshes an existing long-lived token for another ~60 days. Only
     * valid if the token is at least 24 hours old and not yet expired.
     */
    @GetMapping("/refresh_access_token")
    InstagramLongLivedTokenResponse refreshLongLivedToken(
            @RequestParam("grant_type") String grantType,
            @RequestParam("access_token") String longLivedAccessToken);

    @GetMapping("/v25.0/me")
    InstagramProfileResponse getProfile(
            @RequestParam("fields") String fields,
            @RequestParam("access_token") String accessToken);

    @GetMapping("/v25.0/{igUserId}/media")
    InstagramMediaListResponse getMedia(
            @PathVariable("igUserId") String igUserId,
            @RequestParam("fields") String fields,
            @RequestParam("limit") int limit,
            @RequestParam("access_token") String accessToken);

    /**
     * Account-level day-snapshot metrics ({@code metric_type=total_value}).
     */
    @GetMapping("/v25.0/{igUserId}/insights")
    InstagramInsightsResponse getAccountInsights(
            @PathVariable("igUserId") String igUserId,
            @RequestParam("metric") String metric,
            @RequestParam("period") String period,
            @RequestParam("metric_type") String metricType,
            @RequestParam("access_token") String accessToken);

    /**
     * Audience demographic breakdown for a single dimension (age, gender,
     * city, or country — Instagram does not support combining dimensions in
     * one request).
     */
    @GetMapping("/v25.0/{igUserId}/insights")
    InstagramDemographicsInsightsResponse getDemographics(
            @PathVariable("igUserId") String igUserId,
            @RequestParam("metric") String metric,
            @RequestParam("breakdown") String breakdown,
            @RequestParam("timeframe") String timeframe,
            @RequestParam("metric_type") String metricType,
            @RequestParam("access_token") String accessToken);
}
