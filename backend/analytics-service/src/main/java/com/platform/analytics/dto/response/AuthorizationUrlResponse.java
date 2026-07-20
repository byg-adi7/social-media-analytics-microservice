package com.platform.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response for any platform's {@code GET /api/oauth/{platform}/authorize}
 * endpoint — the URL the frontend should redirect the user's browser to in
 * order to grant access via that platform's consent screen. Shared across
 * platforms since the shape is identical; only the URL's contents differ.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorizationUrlResponse {

    private String authorizationUrl;
}
