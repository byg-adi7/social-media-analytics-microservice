package com.platform.notification.security;

import com.platform.notification.client.AuthServiceClient;
import com.platform.notification.dto.response.TokenValidationResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Intercepts every incoming user-facing request, extracts the Bearer token,
 * and delegates validation to the Auth Service via {@link AuthServiceClient}.
 * Internal-only endpoints (/internal/**) are excluded - they're secured by
 * a shared API key instead, since they're called by other backend services
 * with no end-user JWT in hand (e.g. a scheduled sync job).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthServiceClient authServiceClient;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/swagger-ui", "/v3/api-docs", "/actuator/health", "/internal"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                TokenValidationResponse validation = authServiceClient.validateToken(authHeader);

                if (validation != null && validation.isValid()) {
                    AuthenticatedUser principal = new AuthenticatedUser(
                            validation.getUserId(), validation.getEmail(), validation.getRole());

                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" +
                                    (validation.getRole() != null ? validation.getRole() : "USER"))));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.warn("Auth Service reported token as invalid for path {}", path);
                }
            } catch (Exception ex) {
                log.error("Token validation failed for path {}: {}", path, ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
