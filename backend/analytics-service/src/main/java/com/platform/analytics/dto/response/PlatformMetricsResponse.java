package com.platform.analytics.dto.response;

import com.platform.analytics.constant.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-platform metric snapshot, used by platform comparison, top-platform,
 * and engagement-distribution endpoints.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformMetricsResponse {

    private Platform platform;
    private long followers;
    private long views;
    private long likes;
    private long comments;
    private long shares;
    private long posts;
    private double engagementRate;
    private double growthRate;
}
