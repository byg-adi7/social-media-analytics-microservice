package com.platform.analytics.youtube.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTOs for {@code GET /youtube/v3/channels}. Only the fields the
 * Analytics Service actually needs are mapped; every record is tolerant of
 * unknown properties since the real YouTube API returns many more fields.
 */
public final class YouTubeChannelListResponse {

    private YouTubeChannelListResponse() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, Snippet snippet, Statistics statistics) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(String title, String description, Thumbnails thumbnails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thumbnails(@JsonProperty("default") Thumbnail defaultThumb) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thumbnail(String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Statistics(
            @JsonProperty("viewCount") String viewCount,
            @JsonProperty("subscriberCount") String subscriberCount,
            @JsonProperty("videoCount") String videoCount
    ) {
    }
}
