package com.platform.analytics.youtube.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.security.StateTokenService;
import com.platform.analytics.youtube.YouTubeProperties;
import com.platform.analytics.youtube.api.dto.GoogleTokenResponse;
import com.platform.analytics.youtube.service.YouTubeOAuthService;
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
 * Implements the Google OAuth 2.0 authorization-code flow for connecting a
 * real YouTube account.
 * <p>
 * Note on tooling: the rest of this service prefers Feign for outbound
 * calls (see {@link com.platform.analytics.youtube.api.YouTubeDataApiClient}),
 * but Google's token endpoint requires an
 * {@code application/x-www-form-urlencoded} body, and Feign's default
 * encoder in this project is JSON. Rather than pull in an extra
 * form-encoding dependency for a single endpoint, this service uses the
 * JDK's built-in {@link HttpClient}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeOAuthServiceImpl implements YouTubeOAuthService {

    private final YouTubeProperties youTubeProperties;
    private final StateTokenService stateTokenService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String buildAuthorizationUrl(UUID userId) {
        String state = stateTokenService.generateState(userId);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", youTubeProperties.getClientId());
        params.put("redirect_uri", youTubeProperties.getRedirectUri());
        params.put("response_type", "code");
        params.put("scope", youTubeProperties.getScope());
        params.put("access_type", "offline"); // required to receive a refresh_token
        params.put("prompt", "consent");       // ensures a refresh_token is returned even on re-connect
        params.put("state", state);

        String queryString = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        return youTubeProperties.getAuthUri() + "?" + queryString;
    }

    @Override
    public GoogleTokenResponse exchangeCodeForTokens(String authorizationCode) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("code", authorizationCode);
        form.put("client_id", youTubeProperties.getClientId());
        form.put("client_secret", youTubeProperties.getClientSecret());
        form.put("redirect_uri", youTubeProperties.getRedirectUri());
        form.put("grant_type", "authorization_code");

        return postForToken(form, "exchange authorization code");
    }

    @Override
    public GoogleTokenResponse refreshAccessToken(String refreshToken) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("refresh_token", refreshToken);
        form.put("client_id", youTubeProperties.getClientId());
        form.put("client_secret", youTubeProperties.getClientSecret());
        form.put("grant_type", "refresh_token");

        return postForToken(form, "refresh access token");
    }

    private GoogleTokenResponse postForToken(Map<String, String> form, String actionDescription) {
        String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(youTubeProperties.getTokenUri()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), GoogleTokenResponse.class);
            }

            log.error("Google OAuth token endpoint returned {} while trying to {}: {}",
                    response.statusCode(), actionDescription, response.body());
            throw new ExternalApiException(
                    "Failed to " + actionDescription + " — Google returned HTTP " + response.statusCode());
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
