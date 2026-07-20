package com.platform.analytics.spotify.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response DTO for {@code GET /v1/me/top/artists} (requires
 * {@code user-top-read}). Only the fields this service actually uses are
 * mapped; the real API returns additional fields (external_urls, href,
 * uri, type).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotifyTopArtistsResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, String name, Integer popularity, Followers followers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Followers(long total) {
    }
}
