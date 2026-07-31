package com.platform.analytics.tiktok;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code tiktok.*} configuration block from application.yml.
 * Holds the TikTok Login Kit (OAuth 2.0) / Display API settings needed for
 * the real TikTok integration (see {@link TikTokSocialMediaClient}).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tiktok")
public class TikTokProperties {

    /**
     * Master switch. When false, the real TikTok client bean is not
     * created and {@code MockSocialMediaClient} continues to handle
     * TikTok accounts, exactly as before.
     */
    private boolean enabled = false;

    private String clientKey;
    private String clientSecret;
    private String redirectUri;
    private String scope = "user.info.basic,user.info.profile,user.info.stats,video.list";
    private String authUri = "https://www.tiktok.com/v2/auth/authorize/";
    private String tokenUri = "https://open.tiktokapis.com/v2/oauth/token/";
    private String apiBaseUrl = "https://open.tiktokapis.com";

    /**
     * Where to send the user's browser after the OAuth callback completes.
     * The frontend is a mobile app (Expo/React Native), not a web page, so
     * this is a deep link (app.json's "scheme") the app registers a
     * listener for via Linking - not an http(s) URL.
     */
    private String frontendRedirectUri = "audience-insights://oauth-callback";
}
