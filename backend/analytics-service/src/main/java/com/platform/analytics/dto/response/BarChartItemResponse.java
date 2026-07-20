package com.platform.analytics.dto.response;

import com.platform.analytics.constant.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single bar entry for {@code GET /api/charts/platform-comparison}, rendered
 * by the frontend as a bar chart.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BarChartItemResponse {

    private Platform platform;
    private long followers;
}
