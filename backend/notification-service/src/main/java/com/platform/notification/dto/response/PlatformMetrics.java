package com.platform.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirrors com.platform.analytics.dto.response.PlatformMetricsResponse's
 * field shape, for deserializing GET /api/analytics/platform-comparison.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlatformMetrics {
    private AnalyticsPlatform platform;
    private long followers;
    private long views;
    private long likes;
    private long comments;
    private long shares;
    private long posts;
    private double engagementRate;
    private double growthRate;
}
