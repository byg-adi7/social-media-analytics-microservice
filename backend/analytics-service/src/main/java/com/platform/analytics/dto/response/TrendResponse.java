package com.platform.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response for {@code GET /api/analytics/trends} — time-series trend data
 * with computed moving average, suitable for direct frontend consumption.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendResponse {

    private List<String> labels;
    private List<Long> followers;
    private List<Long> views;
    private List<Double> engagementRate;
    private List<Double> movingAverage;
    private double percentageChange;
    private String trendDirection; // UP, DOWN, STABLE
}
