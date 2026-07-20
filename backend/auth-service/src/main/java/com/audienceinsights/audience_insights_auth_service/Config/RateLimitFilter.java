package com.audienceinsights.audience_insights_auth_service.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Basic brute-force / registration-spam protection for /api/auth/login and
 * /api/auth/register - the only two endpoints in this service with no
 * other protection against a client hammering them (login has no account
 * lockout, register has no CAPTCHA/verification step).
 * <p>
 * In-memory, per-instance fixed-window counter, not a distributed
 * rate limiter - correct for this deployment (one auth-service instance)
 * but would under-count if this service were horizontally scaled without
 * a shared store (e.g. Redis) backing the limit instead.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final long WINDOW_MILLIS = 60_000;

    private final Map<String, Window> windowsByKey = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!isRateLimited(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        if (!allow(key)) {
            respondTooManyRequests(response, request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.equals("/api/auth/login") || path.equals("/api/auth/register");
    }

    private String clientKey(HttpServletRequest request) {
        // Prefer the real client IP nginx forwards (proxy_set_header
        // X-Real-IP $remote_addr in nginx.conf) - without this, every
        // request arriving through the gateway would share one key (the
        // gateway container's own address), rate-limiting all users
        // together instead of individually.
        String realIp = request.getHeader("X-Real-IP");
        String ip = (realIp != null && !realIp.isBlank()) ? realIp : request.getRemoteAddr();
        return ip + ":" + request.getRequestURI();
    }

    private boolean allow(String key) {
        Window window = windowsByKey.computeIfAbsent(key, k -> new Window());
        return window.tryConsume();
    }

    private void respondTooManyRequests(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                "error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "message", "Too many requests. Please try again later.",
                "path", path);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /** Fixed window, reset once WINDOW_MILLIS has elapsed since it started. */
    private static final class Window {
        private final ReentrantLock lock = new ReentrantLock();
        private long windowStart = System.currentTimeMillis();
        private int count = 0;

        boolean tryConsume() {
            lock.lock();
            try {
                long now = System.currentTimeMillis();
                if (now - windowStart >= WINDOW_MILLIS) {
                    windowStart = now;
                    count = 0;
                }
                if (count >= MAX_REQUESTS_PER_WINDOW) {
                    return false;
                }
                count++;
                return true;
            } finally {
                lock.unlock();
            }
        }
    }
}
