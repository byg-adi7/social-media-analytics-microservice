package com.platform.analytics.tiktok.service;

import com.platform.analytics.tiktok.api.dto.TikTokTokenResponse;

import java.util.UUID;

/**
 * Handles the TikTok Login Kit OAuth 2.0 authorization-code flow used to
 * connect a real TikTok account: building the consent-screen URL,
 * exchanging the returned authorization code for tokens, and refreshing an
 * expired access token using the stored refresh token.
 */
public interface TikTokOAuthService {

    /**
     * Builds the TikTok consent-screen URL the frontend should redirect the
     * user to, embedding a signed {@code state} parameter that identifies
     * {@code userId} when the callback fires.
     */
    String buildAuthorizationUrl(UUID userId);

    /**
     * Exchanges a one-time authorization code (from the OAuth callback) for
     * an access token + refresh token.
     */
    TikTokTokenResponse exchangeCodeForTokens(String authorizationCode);

    /**
     * Exchanges a stored refresh token for a fresh access token. Unlike
     * Google, TikTok returns a new refresh token on every call — callers
     * should always re-store it.
     */
    TikTokTokenResponse refreshAccessToken(String refreshToken);
}
