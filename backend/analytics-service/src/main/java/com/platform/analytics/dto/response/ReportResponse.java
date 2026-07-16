package com.platform.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response for {@code GET /api/analytics/report} — a full report combining
 * summary, platform comparison, growth, engagement, top posts, best/worst
 * days and recommendations.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportResponse {

    private AnalyticsSummaryResponse summary;
    private List<PlatformMetricsResponse> platformComparison;
    private GrowthResponse audienceGrowth;
    private EngagementResponse engagementAnalysis;
    private List<TopContentResponse> topPosts;
    private List<String> bestDays;
    private List<String> worstDays;
    private List<String> recommendations;
}
