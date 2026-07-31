package com.platform.analytics.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code firebase.*} configuration block for the Firebase Admin
 * SDK / FCM push integration. Disabled by default - same
 * enabled-flag-per-integration pattern as youtube/spotify/instagram/etc,
 * so the app runs fine with push notifications simply skipped until real
 * credentials are supplied.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "firebase")
public class FirebaseProperties {

    private boolean enabled = false;

    /**
     * Base64-encoded Firebase service-account JSON key. The recommended way
     * to supply credentials in a container/PaaS deployment (Render, etc.)
     * where mounting a credentials file isn't practical - a single env var.
     */
    private String serviceAccountBase64;

    /**
     * Alternative to serviceAccountBase64 for local development: a filesystem
     * path to the service-account JSON key. Ignored if serviceAccountBase64
     * is set.
     */
    private String serviceAccountPath;
}
