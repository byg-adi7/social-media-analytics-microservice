package com.platform.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Shared secret checked on /internal/** endpoints, which other backend
 * services (not end users) call. Not part of Spring Security's own
 * authentication - see SecurityConfig's class comment.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "internal-api")
public class InternalApiProperties {
    private String key;
}
