package com.platform.analytics.instagram;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code instagram.*} configuration block from application.yml.
 * Holds the "Business Login for Instagram" OAuth 2.0 / Instagram Graph API
 * settings needed for the real Instagram integration (see
 * {@link InstagramSocialMediaClient}).
 * <p>
 * This uses Meta's Instagram-only login flow ({@code instagram.com/oauth/authorize}),
 * not the older Facebook Login for Business flow — it does not require the
 * connected account to be linked to a Facebook Page, only to be an
 * Instagram Business or Creator professional account.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "instagram")
public class InstagramProperties {

    /**
     * Master switch. When false, the real Instagram client bean is not
     * created and {@code MockSocialMediaClient} continues to handle
     * Instagram accounts, exactly as before.
     */
    private boolean enabled = false;

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scope = "instagram_business_basic instagram_business_manage_insights";
    private String authUri = "https://www.instagram.com/oauth/authorize";
    private String shortLivedTokenUri = "https://api.instagram.com/oauth/access_token";
    private String graphBaseUrl = "https://graph.instagram.com";

    /**
     * Where to send the user's browser after the OAuth callback completes.
     * The frontend is a mobile app (Expo/React Native), not a web page, so
     * this is a deep link (app.json's "scheme") the app registers a
     * listener for via Linking - not an http(s) URL.
     */
    private String frontendRedirectUri = "audience-insights://oauth-callback";
}
