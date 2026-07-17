package com.platform.analytics.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code oauth.*} configuration block — settings shared by every
 * platform's OAuth connect flow, not specific to any single platform.
 * Currently just the HMAC secret used to sign the {@code state} parameter
 * (see {@link com.platform.analytics.security.StateTokenService}), since
 * every platform's OAuth callback is a public endpoint that identifies the
 * connecting user via that signed state rather than a JWT.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    /**
     * Secret used to HMAC-sign the OAuth {@code state} parameter so a
     * platform's callback (a public, unauthenticated endpoint) can trust
     * which user initiated the flow without needing a server-side session
     * store.
     */
    private String stateSecret = "dev-only-change-me-in-production";
}
