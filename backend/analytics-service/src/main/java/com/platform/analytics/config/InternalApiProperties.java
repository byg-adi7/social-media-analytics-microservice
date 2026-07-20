package com.platform.analytics.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Shared secret this service presents when calling the Notification
 * Service's /internal/** endpoints. Must match internal-api.key over there.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "internal-api")
public class InternalApiProperties {
    private String key;
}
