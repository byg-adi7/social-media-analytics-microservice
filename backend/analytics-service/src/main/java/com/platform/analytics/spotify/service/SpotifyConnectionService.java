package com.platform.analytics.spotify.service;

import com.platform.analytics.dto.response.SocialAccountResponse;

import java.util.UUID;

/**
 * Orchestrates the end-to-end flow of connecting a real Spotify account:
 * generating the consent URL, and — once the user approves and Spotify
 * redirects back — exchanging the authorization code for tokens, fetching
 * the profile, and persisting/updating the
 * {@link com.platform.analytics.entity.SocialAccount}.
 */
public interface SpotifyConnectionService {

    /**
     * Builds the Spotify OAuth consent-screen URL for the given user.
     */
    String getAuthorizationUrl(UUID userId);

    /**
     * Completes the OAuth flow after Spotify redirects back with a
     * {@code code} and {@code state}. Verifies {@code state}, exchanges the
     * code for tokens, fetches the profile, and creates/updates the
     * corresponding {@link com.platform.analytics.entity.SocialAccount}.
     */
    SocialAccountResponse completeConnection(String code, String state);
}
