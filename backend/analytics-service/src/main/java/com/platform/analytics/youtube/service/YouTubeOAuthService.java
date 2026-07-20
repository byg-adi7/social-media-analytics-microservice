package com.platform.analytics.youtube.service;

import com.platform.analytics.youtube.api.dto.GoogleTokenResponse;

import java.util.UUID;

/**
 * Handles the Google OAuth 2.0 authorization-code flow used to connect a
 * real YouTube account: building the consent-screen URL, exchanging the
 * returned authorization code for tokens, and refreshing an expired access
 * token using the stored refresh token.
 */
public interface YouTubeOAuthService {

    /**
     * Builds the Google consent-screen URL the frontend should redirect the
     * user to, embedding a signed {@code state} parameter that identifies
     * {@code userId} when the callback fires.
     */
    String buildAuthorizationUrl(UUID userId);

    /**
     * Exchanges a one-time authorization code (from the OAuth callback) for
     * an access token + refresh token.
     */
    GoogleTokenResponse exchangeCodeForTokens(String authorizationCode);

    /**
     * Exchanges a stored refresh token for a fresh access token. Google
     * typically does not return a new refresh token on this call — callers
     * should keep the original.
     */
    GoogleTokenResponse refreshAccessToken(String refreshToken);
}
