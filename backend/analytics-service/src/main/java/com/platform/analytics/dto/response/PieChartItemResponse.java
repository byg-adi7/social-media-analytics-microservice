package com.platform.analytics.dto.response;

import com.platform.analytics.constant.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single slice entry for {@code GET /api/charts/engagement-distribution},
 * rendered by the frontend as a pie chart.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PieChartItemResponse {

    private Platform platform;
    private double value;
}
