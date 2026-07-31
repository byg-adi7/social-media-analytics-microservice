package com.platform.analytics.facebook;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code facebook.*} configuration block from application.yml.
 * Holds the Facebook Login / Graph API Page settings needed for the real
 * Facebook integration (see {@link FacebookSocialMediaClient}).
 * <p>
 * Unlike every other platform in this service, a connected Facebook
 * account is identified by a Facebook <b>Page</b> (not the logging-in
 * person themselves) — see {@link com.platform.analytics.facebook.service.impl.FacebookConnectionServiceImpl}
 * for the user-token-to-page-token exchange this requires.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "facebook")
public class FacebookProperties {

    /**
     * Master switch. When false, the real Facebook client bean is not
     * created and {@code MockSocialMediaClient} continues to handle
     * Facebook accounts, exactly as before.
     */
    private boolean enabled = false;

    private String appId;
    private String appSecret;
    private String redirectUri;
    private String scope = "pages_show_list,pages_read_engagement,pages_read_user_content,read_insights";
    private String authUri = "https://www.facebook.com/v25.0/dialog/oauth";
    private String graphBaseUrl = "https://graph.facebook.com/v25.0";

    /**
     * Where to send the user's browser after the OAuth callback completes.
     * The frontend is a mobile app (Expo/React Native), not a web page, so
     * this is a deep link (app.json's "scheme") the app registers a
     * listener for via Linking - not an http(s) URL.
     */
    private String frontendRedirectUri = "audience-insights://oauth-callback";
}
