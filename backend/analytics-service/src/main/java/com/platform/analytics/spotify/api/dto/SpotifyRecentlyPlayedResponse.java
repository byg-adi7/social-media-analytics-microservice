package com.platform.analytics.spotify.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for {@code GET /v1/me/player/recently-played} (requires
 * {@code user-read-recently-played}). Spotify does not expose full
 * listening history via this endpoint — only a rolling window of the most
 * recent plays (capped at 50 per request) — so anything derived from it
 * (see {@link com.platform.analytics.spotify.SpotifySocialMediaClient}) is
 * a recent-activity snapshot, not a true daily total.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotifyRecentlyPlayedResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(@JsonProperty("played_at") String playedAt, Track track) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Track(String id, String name, @JsonProperty("duration_ms") long durationMs) {
    }
}
