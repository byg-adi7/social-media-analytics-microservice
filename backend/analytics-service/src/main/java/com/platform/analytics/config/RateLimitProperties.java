package com.platform.analytics.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code rate-limit.*} configuration block.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** Requests allowed per window, per user (or per IP for unauthenticated requests). */
    private int capacity = 120;

    private int refillPeriodSeconds = 60;
}
