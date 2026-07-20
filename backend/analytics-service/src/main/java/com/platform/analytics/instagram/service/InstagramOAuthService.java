package com.platform.analytics.instagram.service;

import com.platform.analytics.instagram.api.dto.InstagramLongLivedTokenResponse;
import com.platform.analytics.instagram.api.dto.InstagramShortLivedTokenResponse;

import java.util.UUID;

/**
 * Implements Meta's "Business Login for Instagram" OAuth 2.0 flow, which is
 * a three-step token dance rather than the two-step code/refresh-token
 * pattern used by YouTube and Spotify:
 * <ol>
 *   <li>Authorization code → short-lived access token (~1 hour)</li>
 *   <li>Short-lived token → long-lived access token (~60 days)</li>
 *   <li>Long-lived token → refreshed long-lived token (repeatable, anytime
 *       after 24h from issuance and before expiry)</li>
 * </ol>
 * Instagram has no separate refresh token — the long-lived access token
 * itself is what gets refreshed.
 */
public interface InstagramOAuthService {

    String buildAuthorizationUrl(UUID userId);

    InstagramShortLivedTokenResponse exchangeCodeForShortLivedToken(String authorizationCode);

    InstagramLongLivedTokenResponse exchangeForLongLivedToken(String shortLivedAccessToken);

    InstagramLongLivedTokenResponse refreshLongLivedToken(String longLivedAccessToken);
}
