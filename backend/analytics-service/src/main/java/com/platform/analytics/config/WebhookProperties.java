package com.platform.analytics.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code webhook.*} configuration block.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "webhook")
public class WebhookProperties {

    /**
     * Shared secret this server expects in the X-Webhook-Secret header of
     * the Supabase Database Webhook call fired on auth.users DELETE.
     */
    private String userDeletionSecret;
}
