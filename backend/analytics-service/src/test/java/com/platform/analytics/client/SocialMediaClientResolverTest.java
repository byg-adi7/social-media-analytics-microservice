package com.platform.analytics.client;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.exception.AnalyticsException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialMediaClientResolverTest {

    @Test
    void resolve_prefersRealClientOverMock_whenBothSupportPlatform() {
        MockSocialMediaClient mockClient = mock(MockSocialMediaClient.class);
        when(mockClient.supports(Platform.YOUTUBE)).thenReturn(true);

        SocialMediaClient realClient = mock(SocialMediaClient.class);
        when(realClient.supports(Platform.YOUTUBE)).thenReturn(true);

        SocialMediaClientResolver resolver = new SocialMediaClientResolver(List.of(mockClient, realClient));

        SocialMediaClient resolved = resolver.resolve(Platform.YOUTUBE);

        assertThat(resolved).isSameAs(realClient);
    }

    @Test
    void resolve_fallsBackToMock_whenNoRealClientSupportsPlatform() {
        MockSocialMediaClient mockClient = mock(MockSocialMediaClient.class);
        when(mockClient.supports(Platform.INSTAGRAM)).thenReturn(true);

        SocialMediaClientResolver resolver = new SocialMediaClientResolver(List.of(mockClient));

        SocialMediaClient resolved = resolver.resolve(Platform.INSTAGRAM);

        assertThat(resolved).isSameAs(mockClient);
    }

    @Test
    void resolve_throwsAnalyticsException_whenNoClientSupportsPlatform() {
        SocialMediaClient unrelatedClient = mock(SocialMediaClient.class);
        when(unrelatedClient.supports(Platform.TIKTOK)).thenReturn(false);

        SocialMediaClientResolver resolver = new SocialMediaClientResolver(List.of(unrelatedClient));

        assertThatThrownBy(() -> resolver.resolve(Platform.TIKTOK))
                .isInstanceOf(AnalyticsException.class);
    }
}
