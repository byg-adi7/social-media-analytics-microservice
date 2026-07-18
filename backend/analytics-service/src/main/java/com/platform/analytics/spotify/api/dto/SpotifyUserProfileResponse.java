package com.platform.analytics.spotify.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for {@code GET /v1/me} — the connected user's own Spotify
 * profile. {@code product} indicates subscription tier ("premium" /
 * "free" / etc.) and is only present when the request was authorized with
 * the {@code user-read-private} scope; {@code email} similarly requires
 * {@code user-read-email}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotifyUserProfileResponse(
        String id,
        @JsonProperty("display_name") String displayName,
        String email,
        String product,
        Followers followers,
        List<Image> images
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Followers(String href, long total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String url, Integer height, Integer width) {
    }
}
