package com.platform.analytics.youtube;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code youtube.*} configuration block from application.yml.
 * Holds the Google OAuth 2.0 / YouTube Data API v3 settings needed for the
 * real YouTube integration (see {@link YouTubeSocialMediaClient}).
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
     * Where to send the user's browser after the OAuth callback completes.
     * The frontend is a mobile app (Expo/React Native), not a web page, so
     * this is a deep link (app.json's "scheme") the app registers a
     * listener for via Linking - not an http(s) URL.
     */
    private String frontendRedirectUri = "audience-insights://oauth-callback";
}
