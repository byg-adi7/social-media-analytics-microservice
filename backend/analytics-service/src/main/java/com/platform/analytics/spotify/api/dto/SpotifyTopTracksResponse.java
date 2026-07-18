package com.platform.analytics.spotify.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for {@code GET /v1/me/top/tracks} (requires
 * {@code user-top-read}). Only the fields this service actually uses are
 * mapped; the real API returns many more (available_markets, disc_number,
 * explicit, external_ids, preview_url, track_number, etc.).
 * <p>
 * {@code popularity} is Spotify's own 0-100 relative-popularity score —
 * it is NOT a play/stream count. See
 * {@link com.platform.analytics.spotify.SpotifySocialMediaClient} for how
 * (and why) it is mapped into {@code TopContentResponse.views}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotifyTopTracksResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, String name, Integer popularity, Album album, List<Artist> artists) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Album(String name, @JsonProperty("release_date") String releaseDate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artist(String id, String name) {
    }
}
