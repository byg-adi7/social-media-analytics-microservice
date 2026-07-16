package com.platform.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response for {@code GET /api/analytics/growth} — growth-rate predictions
 * and historical follower delta.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrowthResponse {

    private long startFollowers;
    private long endFollowers;
    private long followerDifference;
    private double growthRatePercentage;
    private double averageDailyGrowth;
    private double averageWeeklyGrowth;
    private double averageMonthlyGrowth;
    private long predictedFollowersNext30Days;
}
