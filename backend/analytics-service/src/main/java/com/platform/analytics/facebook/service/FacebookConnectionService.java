package com.platform.analytics.facebook.service;

import com.platform.analytics.dto.response.SocialAccountResponse;

import java.util.UUID;

/**
 * Orchestrates the end-to-end flow of connecting a real Facebook Page:
 * generating the Facebook consent URL, and — once the user approves and
 * Facebook redirects back — exchanging the authorization code for a
 * short-lived user token, upgrading it to a long-lived user token, fetching
 * the first Facebook Page the user manages and its (non-expiring) Page
 * access token, and persisting/updating the
 * {@link com.platform.analytics.entity.SocialAccount}.
 * <p>
 * If the connecting person manages multiple Facebook Pages, only the first
 * one returned is connected — matching the same single-account-per-connect
 * simplification used by the YouTube integration (one channel per connect).
 */
public interface FacebookConnectionService {

    /**
     * Builds the Facebook OAuth consent-screen URL for the given user.
     */
    String getAuthorizationUrl(UUID userId);

    /**
     * Completes the OAuth flow after Facebook redirects back with a
     * {@code code} and {@code state}. Verifies {@code state}, walks the
     * user-token exchange, fetches the Page identity and Page access token,
     * and creates/updates the corresponding
     * {@link com.platform.analytics.entity.SocialAccount}.
     */
    SocialAccountResponse completeConnection(String code, String state);
}
