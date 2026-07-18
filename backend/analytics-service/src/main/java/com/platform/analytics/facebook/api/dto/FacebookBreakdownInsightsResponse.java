package com.platform.analytics.facebook.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for breakdown-shaped Page insights metrics, where
 * {@code value} is an object of key-value pairs rather than a single
 * number — e.g. {@code page_actions_post_reactions_total} (reaction type ->
 * count) or {@code page_follows_city} / {@code page_follows_country}
 * (location name -> count).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FacebookBreakdownInsightsResponse(List<Metric> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metric(String name, String period, List<ValueEntry> values) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValueEntry(Map<String, Long> value) {
    }
}
