package com.platform.analytics.tiktok.api;

import com.platform.analytics.tiktok.api.dto.TikTokUserInfoResponse;
import com.platform.analytics.tiktok.api.dto.TikTokVideoListRequest;
import com.platform.analytics.tiktok.api.dto.TikTokVideoListResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for the real TikTok Display API (read-only). All calls
 * authenticate with the connected creator's own OAuth access token — this
 * service never uses a shared app-level token for per-account data, since
 * profile/video statistics require the creator's own consent (the
 * {@code user.info.*} / {@code video.list} scopes granted during connect).
 */
@FeignClient(name = "tiktok-api", url = "${tiktok.api-base-url}")
public interface TikTokApiClient {

    /**
     * Fetches the authenticated user's own profile fields.
     */
    @GetMapping("/v2/user/info/")
    TikTokUserInfoResponse getUserInfo(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestParam("fields") String fields);

    /**
     * Fetches a page of the authenticated user's own videos, most recent
     * first.
     */
    @PostMapping("/v2/video/list/")
    TikTokVideoListResponse listVideos(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestParam("fields") String fields,
            @RequestBody TikTokVideoListRequest request);
}
