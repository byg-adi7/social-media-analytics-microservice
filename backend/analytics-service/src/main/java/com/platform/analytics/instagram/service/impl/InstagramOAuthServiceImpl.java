package com.platform.analytics.instagram.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.instagram.InstagramProperties;
import com.platform.analytics.instagram.api.InstagramApiClient;
import com.platform.analytics.instagram.api.dto.InstagramLongLivedTokenResponse;
import com.platform.analytics.instagram.api.dto.InstagramShortLivedTokenResponse;
import com.platform.analytics.instagram.service.InstagramOAuthService;
import com.platform.analytics.security.StateTokenService;
import feign.FeignException;
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
 * Implements the "Business Login for Instagram" OAuth 2.0 flow.
 * <p>
 * Note on tooling: the initial code-for-short-lived-token exchange targets
 * {@code api.instagram.com} and requires an
 * {@code application/x-www-form-urlencoded} POST body — like
 * {@code YouTubeOAuthServiceImpl}/{@code SpotifyOAuthServiceImpl}, this
 * project's default Feign encoder is JSON, so that one call uses the JDK's
 * {@link HttpClient} instead. The long-lived exchange and refresh calls are
 * plain GETs with query parameters against {@code graph.instagram.com}, so
 * those go through {@link InstagramApiClient} (Feign) like the rest of the
 * platform's API calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramOAuthServiceImpl implements InstagramOAuthService {

    private static final String LONG_LIVED_GRANT_TYPE = "ig_exchange_token";
    private static final String REFRESH_GRANT_TYPE = "ig_refresh_token";

    private final InstagramProperties instagramProperties;
    private final InstagramApiClient instagramApiClient;
    private final StateTokenService stateTokenService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String buildAuthorizationUrl(UUID userId) {
        String state = stateTokenService.generateState(userId);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", instagramProperties.getClientId());
        params.put("redirect_uri", instagramProperties.getRedirectUri());
        params.put("response_type", "code");
        params.put("scope", instagramProperties.getScope());
        params.put("state", state);

        String queryString = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        return instagramProperties.getAuthUri() + "?" + queryString;
    }

    @Override
    public InstagramShortLivedTokenResponse exchangeCodeForShortLivedToken(String authorizationCode) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", instagramProperties.getClientId());
        form.put("client_secret", instagramProperties.getClientSecret());
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", instagramProperties.getRedirectUri());
        form.put("code", authorizationCode);

        String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(instagramProperties.getShortLivedTokenUri()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                InstagramShortLivedTokenResponse parsed =
                        objectMapper.readValue(response.body(), InstagramShortLivedTokenResponse.class);
                // A 2xx status with no `data` entries has been silent until
                // now - the caller only sees a generic "no access token"
                // message with nothing to diagnose it by. Logging Instagram's
                // actual raw body here (most likely an error object under a
                // different shape than expected, e.g. {"error_type":...}) is
                // the only way to tell why without live debugger access.
                if (parsed.data() == null || parsed.data().isEmpty()) {
                    log.error("Instagram short-lived token endpoint returned HTTP {} but no usable token: {}",
                            response.statusCode(), response.body());
                }
                return parsed;
            }

            log.error("Instagram short-lived token endpoint returned {}: {}",
                    response.statusCode(), response.body());
            throw new ExternalApiException(
                    "Failed to exchange authorization code — Instagram returned HTTP " + response.statusCode());
        } catch (ExternalApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to exchange authorization code for Instagram short-lived token: {}", ex.getClass().getSimpleName());
            throw new ExternalApiException("Failed to exchange authorization code for Instagram short-lived token", ex);
        }
    }

    @Override
    public InstagramLongLivedTokenResponse exchangeForLongLivedToken(String shortLivedAccessToken) {
        try {
            return instagramApiClient.exchangeForLongLivedToken(
                    LONG_LIVED_GRANT_TYPE, instagramProperties.getClientSecret(), shortLivedAccessToken);
        } catch (FeignException ex) {
            log.error("Failed to exchange Instagram short-lived token for a long-lived token: HTTP {}", ex.status());
            throw new ExternalApiException("Failed to exchange Instagram short-lived token for a long-lived token", ex);
        }
    }

    @Override
    public InstagramLongLivedTokenResponse refreshLongLivedToken(String longLivedAccessToken) {
        try {
            return instagramApiClient.refreshLongLivedToken(REFRESH_GRANT_TYPE, longLivedAccessToken);
        } catch (FeignException ex) {
            log.error("Failed to refresh Instagram long-lived token: HTTP {}", ex.status());
            throw new ExternalApiException("Failed to refresh Instagram long-lived token", ex);
        }
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
