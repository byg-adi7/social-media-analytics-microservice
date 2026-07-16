package com.platform.analytics.client;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.exception.AnalyticsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves which {@link SocialMediaClient} bean should handle a given
 * platform. When more than one client supports the same platform (e.g.
 * {@link MockSocialMediaClient}, which supports every platform as a
 * fallback, alongside a real integration like
 * {@link YouTubeSocialMediaClient}), the first <em>non-mock</em> client is
 * preferred. This is what lets the service transparently upgrade a
 * platform from mock data to a real API integration purely by toggling
 * configuration (e.g. {@code youtube.enabled=true}) — no changes needed in
 * {@code AnalyticsSyncServiceImpl} or {@code ChartServiceImpl}.
 */
@Component
@RequiredArgsConstructor
public class SocialMediaClientResolver {

    private final List<SocialMediaClient> socialMediaClients;

    public SocialMediaClient resolve(Platform platform) {
        return socialMediaClients.stream()
                .filter(client -> client.supports(platform) && !(client instanceof MockSocialMediaClient))
                .findFirst()
                .or(() -> socialMediaClients.stream()
                        .filter(client -> client.supports(platform))
                        .findFirst())
                .orElseThrow(() -> new AnalyticsException("No SocialMediaClient available for platform: " + platform));
    }
}
