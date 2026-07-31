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
     * every Supabase Database Webhook call (auth.users INSERT/UPDATE/DELETE -
     * see WebhookController). One shared secret across all three since
     * they're all the same trusted caller (Supabase), not three independent
     * integrations.
     */
    private String supabaseSecret;
}
