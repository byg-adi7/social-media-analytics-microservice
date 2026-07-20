package com.platform.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors com.platform.analytics.dto.response.AnalyticsSummaryResponse's
 * field shape, for deserializing GET /api/analytics/summary.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummary {
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
