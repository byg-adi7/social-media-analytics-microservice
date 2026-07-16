package com.platform.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response for {@code GET /api/oauth/youtube/authorize} — the URL the
 * frontend should redirect the user's browser to in order to grant YouTube
 * access via Google's consent screen.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YouTubeAuthorizationUrlResponse {

    private String authorizationUrl;
}
