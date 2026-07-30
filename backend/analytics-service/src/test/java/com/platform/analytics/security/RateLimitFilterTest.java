package com.platform.analytics.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.analytics.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private RateLimitProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setCapacity(3);
        properties.setRefillPeriodSeconds(60);
        filter = new RateLimitFilter(properties, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsRequestsUpToCapacity_thenRejectsWithTooManyRequests() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200); // MockHttpServletResponse defaults to 200 unless set
        }

        MockHttpServletRequest fourthRequest = new MockHttpServletRequest("GET", "/api/accounts");
        fourthRequest.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse fourthResponse = new MockHttpServletResponse();
        FilterChain fourthChain = mock(FilterChain.class);

        filter.doFilter(fourthRequest, fourthResponse, fourthChain);

        assertThat(fourthResponse.getStatus()).isEqualTo(429);
        assertThat(fourthResponse.getContentAsString()).contains("TOO_MANY_REQUESTS");
        verify(fourthChain, times(0)).doFilter(fourthRequest, fourthResponse);
    }

    @Test
    void differentIps_getIndependentBuckets() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts");
            request.setRemoteAddr("10.0.0.1");
            filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        // A different IP must not be affected by the first IP's exhausted bucket.
        MockHttpServletRequest otherIpRequest = new MockHttpServletRequest("GET", "/api/accounts");
        otherIpRequest.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse otherIpResponse = new MockHttpServletResponse();
        FilterChain otherIpChain = mock(FilterChain.class);

        filter.doFilter(otherIpRequest, otherIpResponse, otherIpChain);

        verify(otherIpChain, times(1)).doFilter(otherIpRequest, otherIpResponse);
    }

    @Test
    void authenticatedUser_isKeyedByUserId_notIp() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(userId, "user@example.com", "authenticated");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts");
            // Same IP reused every time - if this were IP-keyed it would still exhaust after 3.
            request.setRemoteAddr("10.0.0.9");
            filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        MockHttpServletRequest fourthRequest = new MockHttpServletRequest("GET", "/api/accounts");
        fourthRequest.setRemoteAddr("10.0.0.9");
        MockHttpServletResponse fourthResponse = new MockHttpServletResponse();

        filter.doFilter(fourthRequest, fourthResponse, mock(FilterChain.class));

        assertThat(fourthResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void healthEndpoint_isNeverRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
        }
    }

    @Test
    void disabledViaConfig_neverRateLimits() throws Exception {
        properties.setEnabled(false);

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
        }
    }

    @Test
    void xForwardedFor_isUsedOverRemoteAddr() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts");
            request.setRemoteAddr("192.168.1.1"); // e.g. Render's internal proxy address
            request.addHeader("X-Forwarded-For", "203.0.113.5, 192.168.1.1");
            filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        MockHttpServletRequest fourthRequest = new MockHttpServletRequest("GET", "/api/accounts");
        fourthRequest.setRemoteAddr("192.168.1.1");
        fourthRequest.addHeader("X-Forwarded-For", "203.0.113.5, 192.168.1.1");
        MockHttpServletResponse fourthResponse = new MockHttpServletResponse();

        filter.doFilter(fourthRequest, fourthResponse, mock(FilterChain.class));

        // Same real client IP (203.0.113.5) across all 4 requests despite a
        // shared proxy remote address - must still be rate limited together.
        assertThat(fourthResponse.getStatus()).isEqualTo(429);
    }
}
