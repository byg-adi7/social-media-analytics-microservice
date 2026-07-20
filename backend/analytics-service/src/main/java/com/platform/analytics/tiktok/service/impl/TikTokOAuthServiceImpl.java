package com.platform.analytics.tiktok.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.security.StateTokenService;
import com.platform.analytics.tiktok.TikTokProperties;
import com.platform.analytics.tiktok.api.dto.TikTokTokenResponse;
import com.platform.analytics.tiktok.service.TikTokOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements the TikTok Login Kit OAuth 2.0 authorization-code flow for
 * connecting a real TikTok account.
 * <p>
 * Note on tooling: the rest of this service prefers Feign for outbound
 * calls (see {@link com.platform.analytics.tiktok.api.TikTokApiClient}),
 * but TikTok's token endpoint requires an
 * {@code application/x-www-form-urlencoded} body, and Feign's default
 * encoder in this project is JSON. Rather than pull in an extra
 * form-encoding dependency for a single endpoint, this service uses the
 * JDK's built-in {@link HttpClient} — the same approach used for YouTube.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TikTokOAuthServiceImpl implements TikTokOAuthService {

    private final TikTokProperties tikTokProperties;
    private final StateTokenService stateTokenService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String buildAuthorizationUrl(UUID userId) {
        String state = stateTokenService.generateState(userId);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_key", tikTokProperties.getClientKey());
        params.put("response_type", "code");
        params.put("scope", tikTokProperties.getScope());
        params.put("redirect_uri", tikTokProperties.getRedirectUri());
        params.put("state", state);

        String queryString = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        return tikTokProperties.getAuthUri() + "?" + queryString;
    }

    @Override
    public TikTokTokenResponse exchangeCodeForTokens(String authorizationCode) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_key", tikTokProperties.getClientKey());
        form.put("client_secret", tikTokProperties.getClientSecret());
        form.put("code", authorizationCode);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", tikTokProperties.getRedirectUri());

        return postForToken(form, "exchange authorization code");
    }

    @Override
    public TikTokTokenResponse refreshAccessToken(String refreshToken) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_key", tikTokProperties.getClientKey());
        form.put("client_secret", tikTokProperties.getClientSecret());
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);

        return postForToken(form, "refresh access token");
    }

    private TikTokTokenResponse postForToken(Map<String, String> form, String actionDescription) {
        String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tikTokProperties.getTokenUri()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cache-Control", "no-cache")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                TikTokTokenResponse parsed = objectMapper.readValue(response.body(), TikTokTokenResponse.class);
                if (parsed.accessToken() == null || parsed.accessToken().isBlank()) {
                    log.error("TikTok token endpoint returned 200 with no access_token while trying to {}: {}",
                            actionDescription, response.body());
                    throw new ExternalApiException("Failed to " + actionDescription + " — TikTok returned no access token");
                }
                return parsed;
            }

            log.error("TikTok OAuth token endpoint returned {} while trying to {}: {}",
                    response.statusCode(), actionDescription, response.body());
            throw new ExternalApiException(
                    "Failed to " + actionDescription + " — TikTok returned HTTP " + response.statusCode());
        } catch (ExternalApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to {}: {}", actionDescription, ex.getMessage());
            throw new ExternalApiException("Failed to " + actionDescription, ex);
        }
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
