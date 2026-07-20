package com.platform.analytics.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Binds the {@code cors.*} configuration block. Replaces a wildcard CORS
 * origin with an explicit, environment-configured allow-list, since
 * {@code allowedOriginPatterns("*")} combined with {@code allowCredentials
 * (true)} (the previous configuration) permits any origin to make
 * credentialed requests against the API.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    /**
     * Comma-separated list of allowed origins, e.g.
     * {@code http://localhost:3000,https://app.example.com}.
     */
    private String allowedOrigins = "http://localhost:3000";

    public List<String> getAllowedOriginsList() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
