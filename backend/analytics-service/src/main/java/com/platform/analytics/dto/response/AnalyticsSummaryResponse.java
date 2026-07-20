package com.platform.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response for {@code GET /api/analytics/summary} — a condensed overview
 * of account performance across all connected platforms.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsSummaryResponse {

    private long totalFollowers;
    private long totalPosts;
    private double averageEngagementRate;
    private double averageDailyViews;
    private double averageReach;
    private String bestPlatform;
    private String worstPlatform;
    private String fastestGrowingPlatform;
    private String mostActivePlatform;
    private String mostViewedPlatform;
}
