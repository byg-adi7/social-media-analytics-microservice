package com.platform.analytics.service;

import com.platform.analytics.dto.request.AnalyticsQueryRequest;
import com.platform.analytics.dto.response.*;

import java.util.List;
import java.util.UUID;

/**
 * Prepares chart-ready JSON datasets for the frontend. This service never
 * renders charts itself — it only shapes data into the structures the
 * frontend charting library expects (multi-line, bar, pie).
 */
public interface ChartService {

    MultiLineChartResponse getEngagementChart(UUID userId, AnalyticsQueryRequest query);

    MultiLineChartResponse getFollowersChart(UUID userId, AnalyticsQueryRequest query);

    MultiLineChartResponse getViewsChart(UUID userId, AnalyticsQueryRequest query);

    List<BarChartItemResponse> getPlatformComparisonChart(UUID userId);

    List<PieChartItemResponse> getEngagementDistributionChart(UUID userId);

    List<TopContentResponse> getTopContentChart(UUID userId, int limit);

    GrowthChartResponse getWeeklyGrowthChart(UUID userId);

    GrowthChartResponse getMonthlyGrowthChart(UUID userId);
}
