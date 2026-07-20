package com.platform.analytics.spotify.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response DTO for {@code GET /v1/me/following?type=artist} (requires
 * {@code user-follow-read}) — the artists the connected user follows.
 * Only {@code artists.total} is used, as a proxy for the {@code following}
 * count on the connected account.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotifyFollowedArtistsResponse(Artists artists) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artists(long total) {
    }
}
