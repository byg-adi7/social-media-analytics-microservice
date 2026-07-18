package com.platform.analytics.facebook.service;

import com.platform.analytics.facebook.api.dto.FacebookTokenResponse;

import java.util.UUID;

/**
 * Handles the Facebook Login OAuth 2.0 flow used to connect a real Facebook
 * Page: building the consent-screen URL, exchanging the returned
 * authorization code for a short-lived user access token, and exchanging
 * that for a long-lived user access token (~60 days) — the token from
 * which non-expiring Page access tokens are later derived (see
 * {@link com.platform.analytics.facebook.service.impl.FacebookConnectionServiceImpl}).
 */
public interface FacebookOAuthService {

    /**
     * Builds the Facebook consent-screen URL the frontend should redirect
     * the user to, embedding a signed {@code state} parameter that
     * identifies {@code userId} when the callback fires.
     */
    String buildAuthorizationUrl(UUID userId);

    /**
     * Exchanges a one-time authorization code (from the OAuth callback) for
     * a short-lived user access token.
     */
    FacebookTokenResponse exchangeCodeForToken(String authorizationCode);

    /**
     * Exchanges a short-lived user access token for a long-lived one.
     */
    FacebookTokenResponse exchangeForLongLivedToken(String shortLivedUserToken);
}
