package com.platform.analytics.client.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTOs for {@code GET /youtube/v3/videos} — provides per-video
 * view/like/comment counts used both for top-content and (summed across
 * recent uploads) as a proxy for daily engagement metrics.
 */
public final class YouTubeVideoListResponse {

    private YouTubeVideoListResponse() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, Snippet snippet, Statistics statistics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(String title, String publishedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Statistics(
            @JsonProperty("viewCount") String viewCount,
            @JsonProperty("likeCount") String likeCount,
            @JsonProperty("commentCount") String commentCount
    ) {
    }
}
