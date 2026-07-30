package com.platform.analytics.config;

import feign.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared configuration applied to all Feign clients (the real platform
 * OAuth API clients - YouTube, Spotify, Instagram, TikTok, Facebook).
 */
@Configuration
public class FeignClientConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
