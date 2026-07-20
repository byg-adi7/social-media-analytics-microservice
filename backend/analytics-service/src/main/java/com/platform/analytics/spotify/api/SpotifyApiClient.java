package com.platform.analytics.spotify.api;

import com.platform.analytics.spotify.api.dto.SpotifyFollowedArtistsResponse;
import com.platform.analytics.spotify.api.dto.SpotifyRecentlyPlayedResponse;
import com.platform.analytics.spotify.api.dto.SpotifyTopArtistsResponse;
import com.platform.analytics.spotify.api.dto.SpotifyTopTracksResponse;
import com.platform.analytics.spotify.api.dto.SpotifyUserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for the real Spotify Web API. All calls authenticate with
 * the connected user's own OAuth access token — there is no shared
 * application-level API key for personal listening data.
 */
@FeignClient(name = "spotify-api", url = "${spotify.api-base-url}")
public interface SpotifyApiClient {

    /**
     * Fetches the authenticated user's own profile.
     */
    @GetMapping("/me")
    SpotifyUserProfileResponse getCurrentUserProfile(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken);

    /**
     * Fetches the authenticated user's top artists over the given time
     * range ({@code short_term}, {@code medium_term}, or {@code long_term}).
     */
    @GetMapping("/me/top/artists")
    SpotifyTopArtistsResponse getTopArtists(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestParam("time_range") String timeRange,
            @RequestParam("limit") int limit);

    /**
     * Fetches the authenticated user's top tracks over the given time
     * range.
     */
    @GetMapping("/me/top/tracks")
    SpotifyTopTracksResponse getTopTracks(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestParam("time_range") String timeRange,
            @RequestParam("limit") int limit);

    /**
     * Fetches the authenticated user's most recently played tracks
     * (rolling window, capped at 50 by the API — not full listening
     * history).
     */
    @GetMapping("/me/player/recently-played")
    SpotifyRecentlyPlayedResponse getRecentlyPlayed(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestParam("limit") int limit);

    /**
     * Fetches the count of artists the authenticated user follows.
     */
    @GetMapping("/me/following")
    SpotifyFollowedArtistsResponse getFollowedArtists(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestParam("type") String type);
}
