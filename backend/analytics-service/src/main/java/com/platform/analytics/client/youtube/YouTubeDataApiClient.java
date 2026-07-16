package com.platform.analytics.client.youtube;

import com.platform.analytics.client.youtube.dto.YouTubeChannelListResponse;
import com.platform.analytics.client.youtube.dto.YouTubeSearchListResponse;
import com.platform.analytics.client.youtube.dto.YouTubeVideoListResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for the real YouTube Data API v3 (read-only). All calls
 * authenticate with the connected creator's own OAuth access token — this
 * service never uses a shared API key for per-account data, since
 * subscriber/view statistics require the channel owner's consent (the
 * {@code youtube.readonly} scope granted during connect).
 */
@FeignClient(name = "youtube-data-api", url = "${youtube.api-base-url}")
public interface YouTubeDataApiClient {

    /**
     * Fetches the authenticated user's own channel (statistics + snippet).
     */
    @GetMapping("/channels")
    YouTubeChannelListResponse.Response getMyChannel(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestParam("part") String part,
            @RequestParam("mine") boolean mine);

    /**
     * Finds the authenticated user's videos, ordered by view count
     * (used to build the top-content dataset).
     */
    @GetMapping("/search")
    YouTubeSearchListResponse.Response searchMyVideos(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestParam("part") String part,
            @RequestParam("forMine") boolean forMine,
            @RequestParam("type") String type,
            @RequestParam("order") String order,
            @RequestParam("maxResults") int maxResults);

    /**
     * Fetches statistics (views/likes/comments) for a comma-separated list
     * of video ids.
     */
    @GetMapping("/videos")
    YouTubeVideoListResponse.Response getVideoStatistics(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestParam("part") String part,
            @RequestParam("id") String videoIds);
}
