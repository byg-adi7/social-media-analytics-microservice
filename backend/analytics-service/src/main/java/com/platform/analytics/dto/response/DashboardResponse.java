package com.platform.analytics.dto.response;

import com.platform.analytics.constant.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated data returned by {@code GET /api/dashboard}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    private long totalFollowers;
    private long totalViews;
    private long totalLikes;
    private long totalComments;
    private long totalShares;
    private long totalReach;
    private long totalImpressions;
    private double averageEngagement;
    private List<Platform> connectedPlatforms;
    private Platform bestPerformingPlatform;
    private LocalDateTime lastSyncTime;
}
