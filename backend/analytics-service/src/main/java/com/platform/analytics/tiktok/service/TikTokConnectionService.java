package com.platform.analytics.tiktok.service;

import com.platform.analytics.dto.response.SocialAccountResponse;

import java.util.UUID;

/**
 * Orchestrates the end-to-end flow of connecting a real TikTok account:
 * generating the TikTok consent URL, and — once the user approves and
 * TikTok redirects back — exchanging the authorization code for tokens,
 * fetching the account's profile identity, and persisting/updating the
 * {@link com.platform.analytics.entity.SocialAccount}.
 */
public interface TikTokConnectionService {

    /**
     * Builds the TikTok OAuth consent-screen URL for the given user.
     */
    String getAuthorizationUrl(UUID userId);

    /**
     * Completes the OAuth flow after TikTok redirects back with a
     * {@code code} and {@code state}. Verifies {@code state}, exchanges the
     * code for tokens, fetches the profile identity, and creates/updates
     * the corresponding {@link com.platform.analytics.entity.SocialAccount}.
     */
    SocialAccountResponse completeConnection(String code, String state);
}
