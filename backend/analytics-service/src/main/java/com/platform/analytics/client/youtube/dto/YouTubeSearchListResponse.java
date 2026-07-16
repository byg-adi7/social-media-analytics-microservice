package com.platform.analytics.client.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response DTOs for {@code GET /youtube/v3/search} — used to find a
 * channel's most-viewed videos (search returns ids; statistics are then
 * looked up separately via {@link YouTubeVideoListResponse}).
 */
public final class YouTubeSearchListResponse {

    private YouTubeSearchListResponse() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(VideoId id, Snippet snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoId(String videoId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snippet(String title, String publishedAt) {
    }
}
