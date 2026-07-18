package com.platform.analytics.dto.response;

import com.platform.analytics.constant.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

/**
 * Audience composition breakdown for a single connected account, used by
 * {@code GET /api/charts/audience-demographics}. Only populated by platform
 * clients that support it (currently Instagram's {@code follower_demographics}
 * insight) — see {@link com.platform.analytics.client.SocialMediaClient#fetchAudienceDemographics}.
 * <p>
 * Each map's values are follower counts keyed by that dimension's bucket
 * (e.g. {@code byAgeRange} keyed by "18-24", "25-34", ...). Maps may be
 * empty (not null) when the platform returned no data for that dimension,
 * e.g. below its minimum follower/engagement threshold.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AudienceDemographicsResponse {

    private Platform platform;
    private UUID accountId;
    private String accountName;
    private Map<String, Long> byAgeRange;
    private Map<String, Long> byGender;
    private Map<String, Long> byCity;
    private Map<String, Long> byCountry;
}
