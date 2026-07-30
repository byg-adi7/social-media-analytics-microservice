package com.platform.analytics.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analytics.config.RateLimitProperties;
import com.platform.analytics.constant.ErrorCode;
import com.platform.analytics.exception.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token-bucket rate limiter, appropriate for a single-instance
 * deployment (a distributed store like Redis would only be needed if this
 * were ever horizontally scaled). Keyed by authenticated userId when
 * available (this filter runs after {@link JwtAuthenticationFilter}, so a
 * valid token's principal is already in the security context), falling
 * back to client IP for anonymous/unauthenticated requests - which still
 * matters, since a bad or forged token is rejected further down the chain,
 * not by this filter.
 * <p>
 * {@code /actuator/health} is deliberately excluded: Render's own health
 * checks hit it frequently, and rate-limiting it could cause Render to see
 * the service as unhealthy and restart it - a self-inflicted outage.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final List<String> EXCLUDED_PATHS = List.of(
            "/actuator/health", "/swagger-ui", "/v3/api-docs"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled() || isExcluded(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(resolveKey(request), key -> newBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            writeTooManyRequests(response, request.getRequestURI());
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(properties.getCapacity())
                .refillGreedy(properties.getCapacity(), Duration.ofSeconds(properties.getRefillPeriodSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveKey(HttpServletRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return "user:" + user.getUserId();
        }
        return "ip:" + resolveClientIp(request);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Render (and most proxies) prepend the real client IP first.
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isExcluded(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    private void writeTooManyRequests(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .errorCode(ErrorCode.TOO_MANY_REQUESTS)
                .message("Too many requests - please slow down and try again shortly")
                .path(path)
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
