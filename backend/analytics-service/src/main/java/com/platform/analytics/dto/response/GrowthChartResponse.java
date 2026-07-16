package com.platform.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Chart-ready response for {@code GET /api/charts/weekly-growth} and
 * {@code GET /api/charts/monthly-growth} — simple label/value series
 * representing follower growth over time.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrowthChartResponse {

    private List<String> labels;
    private List<Long> followerGrowth;
}
