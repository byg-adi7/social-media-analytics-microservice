package com.platform.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response for {@code GET /api/analytics/engagement}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EngagementResponse {

    private double overallEngagementRate;
    private long totalLikes;
    private long totalComments;
    private long totalShares;
    private long totalSaves;
    private String bestEngagementDay;
    private String worstEngagementDay;
}
