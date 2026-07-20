package com.platform.analytics.facebook.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response DTO for scalar (single-number-per-period) Page/Post insights
 * metrics, e.g. {@code page_follows}, {@code page_media_view},
 * {@code page_total_media_view_unique}, {@code page_post_engagements},
 * {@code post_media_view}. Breakdown-shaped metrics (a map of values, e.g.
 * {@code page_actions_post_reactions_total}) use
 * {@link FacebookBreakdownInsightsResponse} instead.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FacebookInsightsResponse(List<Metric> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metric(String name, String period, List<ValueEntry> values) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValueEntry(Long value) {
    }
}
