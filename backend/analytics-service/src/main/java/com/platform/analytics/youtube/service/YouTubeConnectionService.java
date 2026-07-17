package com.platform.analytics.youtube.service;

import com.platform.analytics.dto.response.SocialAccountResponse;

import java.util.UUID;

/**
 * Orchestrates the end-to-end flow of connecting a real YouTube account:
 * generating the Google consent URL, and — once the user approves and
 * Google redirects back — exchanging the authorization code for tokens,
 * fetching the channel's identity, and persisting/updating the
 * {@link com.platform.analytics.entity.SocialAccount}.
 */
public interface YouTubeConnectionService {

    /**
     * Builds the Google OAuth consent-screen URL for the given user.
     */
    String getAuthorizationUrl(UUID userId);

    /**
     * Completes the OAuth flow after Google redirects back with a
     * {@code code} and {@code state}. Verifies {@code state}, exchanges the
     * code for tokens, fetches the channel identity, and creates/updates
     * the corresponding {@link com.platform.analytics.entity.SocialAccount}.
     */
    SocialAccountResponse completeConnection(String code, String state);
}
