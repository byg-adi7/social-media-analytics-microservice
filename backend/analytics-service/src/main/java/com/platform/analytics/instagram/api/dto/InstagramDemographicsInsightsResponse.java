package com.platform.analytics.instagram.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from {@code GET .../insights?metric=follower_demographics&breakdown={dimension}}.
 * Shape verified against Meta's Instagram Platform Insights reference: a
 * single {@code breakdowns} entry whose {@code results} pair each
 * dimension value (e.g. an age bucket like "25-34") with a follower count.
 * {@code dimension_values} has one entry per {@code dimension_keys} slot —
 * for a single-dimension breakdown request, index 0 is always
 * {@code "timeframe"} and index 1 is the requested dimension (age/gender/
 * city/country), so {@code dimensionValues.get(1)} is the bucket label.
 */
public record InstagramDemographicsInsightsResponse(
        List<Metric> data
) {
    public record Metric(
            String name,
            @JsonProperty("total_value") TotalValue totalValue
    ) {
    }

    public record TotalValue(
            List<Breakdown> breakdowns
    ) {
    }

    public record Breakdown(
            @JsonProperty("dimension_keys") List<String> dimensionKeys,
            List<Result> results
    ) {
    }

    public record Result(
            @JsonProperty("dimension_values") List<String> dimensionValues,
            Long value
    ) {
    }
}
