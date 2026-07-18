package com.platform.analytics.spotify.service;

import com.platform.analytics.spotify.api.dto.SpotifyTokenResponse;

import java.util.UUID;

/**
 * Handles the Spotify OAuth 2.0 authorization-code flow used to connect a
 * real Spotify account: building the consent-screen URL, exchanging the
 * returned authorization code for tokens, and refreshing an expired access
 * token using the stored refresh token.
 */
public interface SpotifyOAuthService {

    /**
     * Builds the Spotify consent-screen URL the frontend should redirect
     * the user to, embedding a signed {@code state} parameter that
     * identifies {@code userId} when the callback fires.
     */
    String buildAuthorizationUrl(UUID userId);

    /**
     * Exchanges a one-time authorization code (from the OAuth callback) for
     * an access token + refresh token.
     */
    SpotifyTokenResponse exchangeCodeForTokens(String authorizationCode);

    /**
     * Exchanges a stored refresh token for a fresh access token. Spotify
     * usually — but not always — returns a new refresh token on this call;
     * callers should keep the original when it's absent.
     */
    SpotifyTokenResponse refreshAccessToken(String refreshToken);
}
