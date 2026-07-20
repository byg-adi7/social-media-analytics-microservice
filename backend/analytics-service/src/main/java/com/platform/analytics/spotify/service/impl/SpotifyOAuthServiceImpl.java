package com.platform.analytics.spotify.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.security.StateTokenService;
import com.platform.analytics.spotify.SpotifyProperties;
import com.platform.analytics.spotify.api.dto.SpotifyTokenResponse;
import com.platform.analytics.spotify.service.SpotifyOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements the Spotify OAuth 2.0 authorization-code flow for connecting a
 * real Spotify account.
 * <p>
 * Note on tooling: like {@code YouTubeOAuthServiceImpl}, this uses the
 * JDK's built-in {@link HttpClient} rather than Feign for the token
 * endpoint, since it requires an {@code application/x-www-form-urlencoded}
 * body and this project's default Feign encoder is JSON.
 * <p>
 * Unlike Google's token endpoint (which accepts {@code client_id}/
 * {@code client_secret} in the form body), Spotify's token endpoint
 * requires them as an HTTP Basic {@code Authorization} header — see
 * {@link #basicAuthHeader()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotifyOAuthServiceImpl implements SpotifyOAuthService {

    private final SpotifyProperties spotifyProperties;
    private final StateTokenService stateTokenService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String buildAuthorizationUrl(UUID userId) {
        String state = stateTokenService.generateState(userId);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", spotifyProperties.getClientId());
        params.put("response_type", "code");
        params.put("redirect_uri", spotifyProperties.getRedirectUri());
        params.put("scope", spotifyProperties.getScope());
        params.put("state", state);

        String queryString = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        return spotifyProperties.getAuthUri() + "?" + queryString;
    }

    @Override
    public SpotifyTokenResponse exchangeCodeForTokens(String authorizationCode) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", authorizationCode);
        form.put("redirect_uri", spotifyProperties.getRedirectUri());

        return postForToken(form, "exchange authorization code");
    }

    @Override
    public SpotifyTokenResponse refreshAccessToken(String refreshToken) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);

        return postForToken(form, "refresh access token");
    }

    private SpotifyTokenResponse postForToken(Map<String, String> form, String actionDescription) {
        String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(spotifyProperties.getTokenUri()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", basicAuthHeader())
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), SpotifyTokenResponse.class);
            }

            log.error("Spotify OAuth token endpoint returned {} while trying to {}: {}",
                    response.statusCode(), actionDescription, response.body());
            throw new ExternalApiException(
                    "Failed to " + actionDescription + " — Spotify returned HTTP " + response.statusCode());
        } catch (ExternalApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to {}: {}", actionDescription, ex.getMessage());
            throw new ExternalApiException("Failed to " + actionDescription, ex);
        }
    }

    private String basicAuthHeader() {
        String credentials = spotifyProperties.getClientId() + ":" + spotifyProperties.getClientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
