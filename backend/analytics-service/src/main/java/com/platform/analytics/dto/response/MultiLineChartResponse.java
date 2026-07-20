package com.platform.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Chart-ready response for multi-line charts (one line per platform), e.g.
 * {@code GET /api/charts/engagement}, {@code /api/charts/followers},
 * {@code /api/charts/views}.
 * <p>
 * {@code series} maps a lowercase platform name (e.g. "youtube") to its
 * ordered list of values, aligned with {@code labels}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiLineChartResponse {

    private List<String> labels;
    private Map<String, List<Long>> series;
}
