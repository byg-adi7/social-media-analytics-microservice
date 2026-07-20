package com.platform.analytics.instagram.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from {@code GET .../insights?metric_type=total_value}, used for
 * the account-level day-snapshot metrics (reach, views, likes, comments,
 * shares, saves). Shape verified against Meta's Instagram Platform Insights
 * reference: each requested metric comes back as one entry in {@code data},
 * with its single number under {@code total_value.value}.
 */
public record InstagramInsightsResponse(
        List<Metric> data
) {
    public record Metric(
            String name,
            String period,
            @JsonProperty("total_value") TotalValue totalValue
    ) {
    }

    public record TotalValue(
            Long value
    ) {
    }
}
