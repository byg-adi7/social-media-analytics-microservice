package com.platform.analytics.config;

import feign.Logger;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared configuration applied to all Feign clients (currently only
 * {@link com.platform.analytics.client.AuthServiceClient}).
 */
@Configuration
public class FeignClientConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new AuthServiceErrorDecoder();
    }

    static class AuthServiceErrorDecoder implements ErrorDecoder {
        private final ErrorDecoder defaultDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            if (response.status() == 401 || response.status() == 403) {
                return new com.platform.analytics.exception.UnauthorizedException(
                        "Auth Service rejected the provided token");
            }
            return defaultDecoder.decode(methodKey, response);
        }
    }
}
