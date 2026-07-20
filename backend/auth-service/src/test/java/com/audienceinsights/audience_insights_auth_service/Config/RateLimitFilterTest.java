package com.audienceinsights.audience_insights_auth_service.Config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives RateLimitFilter directly (no Spring context) so this test can
 * exercise its actual limit-exceeded behavior without leaving poisoned
 * counter state for any other test that happens to share the same Spring
 * application context (RateLimitFilter is a singleton bean with real
 * in-memory state).
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private FilterChain filterChain;

    @Mock
    private HttpServletResponse response;

    private final RateLimitFilter filter = new RateLimitFilter();

    @Test
    void allowsRequests_upToTheLimit_thenRejectsWithTooManyRequests() throws Exception {
        HttpServletRequest request = loginRequestFrom("203.0.113.1");
        when(response.getWriter()).thenReturn(new PrintWriter(java.io.Writer.nullWriter()));

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, filterChain);
        }
        verify(filterChain, times(10)).doFilter(request, response);

        // The 11th request within the window must be rejected, not
        // forwarded to the controller.
        filter.doFilter(request, response, filterChain);
        verify(filterChain, times(10)).doFilter(request, response);
        verify(response).setStatus(429);
    }

    @Test
    void tracksDifferentClientIps_independently() throws Exception {
        HttpServletRequest requestA = loginRequestFrom("203.0.113.10");
        HttpServletRequest requestB = loginRequestFrom("203.0.113.20");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(requestA, response, filterChain);
        }
        // A different client IP must not be affected by A's exhausted limit.
        filter.doFilter(requestB, response, filterChain);

        verify(filterChain, times(10)).doFilter(requestA, response);
        verify(filterChain, times(1)).doFilter(requestB, response);
    }

    @Test
    void doesNotRateLimit_unrelatedPaths() throws Exception {
        HttpServletRequest request = requestFor("GET", "/api/auth/validate", "203.0.113.30");

        for (int i = 0; i < 50; i++) {
            filter.doFilter(request, response, filterChain);
        }

        verify(filterChain, times(50)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    private HttpServletRequest loginRequestFrom(String ip) {
        return requestFor("POST", "/api/auth/login", ip);
    }

    private HttpServletRequest requestFor(String method, String path, String ip) {
        // lenient: a non-POST request short-circuits in isRateLimited()
        // before ever reading getRequestURI(), so that stub goes unused on
        // that path - not a sign of a bad test, just this helper covering
        // both cases.
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        org.mockito.Mockito.lenient().when(request.getMethod()).thenReturn(method);
        org.mockito.Mockito.lenient().when(request.getRequestURI()).thenReturn(path);
        org.mockito.Mockito.lenient().when(request.getHeader("X-Real-IP")).thenReturn(ip);
        return request;
    }
}
