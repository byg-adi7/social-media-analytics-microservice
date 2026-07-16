package com.platform.analytics.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code youtube.*} configuration block from application.yml.
 * Holds the Google OAuth 2.0 / YouTube Data API v3 settings needed for the
 * real YouTube integration (see {@link com.platform.analytics.client.YouTubeSocialMediaClient}).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "youtube")
public class YouTubeProperties {

    /**
     * Master switch. When false, the real YouTube client bean is not
     * created and {@code MockSocialMediaClient} continues to handle
     * YouTube accounts, exactly as before.
     */
    private boolean enabled = false;

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scope = "https://www.googleapis.com/auth/youtube.readonly";
    private String authUri = "https://accounts.google.com/o/oauth2/v2/auth";
    private String tokenUri = "https://oauth2.googleapis.com/token";
    private String apiBaseUrl = "https://www.googleapis.com/youtube/v3";

    /**
     * Where to send the user's browser after the OAuth callback completes
     * (typically a frontend "accounts connected" page).
     */
    private String frontendRedirectUri = "http://localhost:3000/dashboard";

    /**
     * Secret used to HMAC-sign the OAuth {@code state} parameter so the
     * callback (a public, unauthenticated endpoint) can trust which user
     * initiated the flow without needing a server-side session store.
     */
    private String stateSecret = "dev-only-change-me-in-production";
}
