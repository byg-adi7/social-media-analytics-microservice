package com.platform.analytics.client;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.AudienceDemographicsResponse;
import com.platform.analytics.dto.response.TopContentResponse;
import com.platform.analytics.entity.SocialAccount;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Abstraction over an external social media platform's API.
 * <p>
 * {@link MockSocialMediaClient} currently generates realistic synthetic
 * data. Swapping in real integrations later (YouTube Data API, Instagram
 * Graph API, TikTok API, Facebook Graph API) only requires implementing
 * this interface per platform and wiring it in via {@link Platform} — no
 * changes are needed anywhere else in the service.
 */
public interface SocialMediaClient {

    /**
     * @return true if this client implementation supports the given platform.
     */
    boolean supports(Platform platform);

    /**
     * Fetches (or generates) a single day's analytics snapshot for the given
     * connected account and date.
     */
    DailyMetrics fetchDailyMetrics(SocialAccount account, LocalDate date);

    /**
     * Fetches the top-performing content/posts for the given account.
     */
    List<TopContentResponse> fetchTopContent(SocialAccount account, int limit);

    /**
     * Fetches audience demographic breakdowns (age/gender/city/country) for
     * the given account, if the platform and this client implementation
     * support it. Defaults to {@link Optional#empty()} so platforms without
     * a demographics concept (e.g. Spotify's personal-listening data) or
     * clients that haven't implemented it need no changes here.
     */
    default Optional<AudienceDemographicsResponse> fetchAudienceDemographics(SocialAccount account) {
        return Optional.empty();
    }

    /**
     * Plain data carrier for a day's raw metrics pulled from (or simulated
     * for) the external platform, before persistence as an {@code Analytics}
     * entity.
     */
    record DailyMetrics(
            long followers,
            long following,
            long impressions,
            long reach,
            long profileVisits,
            long views,
            double watchTime,
            long likes,
            long comments,
            long shares,
            long saves,
            long posts
    ) {
    }
}
