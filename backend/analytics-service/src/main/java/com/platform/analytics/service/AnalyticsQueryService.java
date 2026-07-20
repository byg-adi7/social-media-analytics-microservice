package com.platform.analytics.service;

import com.platform.analytics.dto.request.AnalyticsQueryRequest;
import com.platform.analytics.dto.response.*;

import java.util.List;
import java.util.UUID;

/**
 * Provides report, trend, comparison, summary, engagement and growth
 * analytics used by the {@code /api/analytics/*} endpoints.
 */
public interface AnalyticsQueryService {

    ReportResponse getReport(UUID userId, AnalyticsQueryRequest query);

    TrendResponse getTrends(UUID userId, AnalyticsQueryRequest query);

    List<PlatformMetricsResponse> getPlatformComparison(UUID userId, AnalyticsQueryRequest query);

    AnalyticsSummaryResponse getSummary(UUID userId, AnalyticsQueryRequest query);

    PlatformMetricsResponse getTopPlatform(UUID userId, AnalyticsQueryRequest query);

    EngagementResponse getEngagement(UUID userId, AnalyticsQueryRequest query);

    GrowthResponse getGrowth(UUID userId, AnalyticsQueryRequest query);
}
