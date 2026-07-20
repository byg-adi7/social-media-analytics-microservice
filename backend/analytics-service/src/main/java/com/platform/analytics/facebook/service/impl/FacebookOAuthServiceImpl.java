package com.platform.analytics.facebook.service.impl;

import com.platform.analytics.exception.ExternalApiException;
import com.platform.analytics.facebook.FacebookProperties;
import com.platform.analytics.facebook.api.FacebookApiClient;
import com.platform.analytics.facebook.api.dto.FacebookTokenResponse;
import com.platform.analytics.facebook.service.FacebookOAuthService;
import com.platform.analytics.security.StateTokenService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements the Facebook Login OAuth 2.0 flow for connecting a real
 * Facebook Page.
 * <p>
 * Note on tooling: unlike YouTube/TikTok, Facebook's token endpoint is a
 * plain {@code GET} with query parameters rather than a
 * form-urlencoded {@code POST} body, so this service goes straight through
 * Feign ({@link FacebookApiClient}) for token exchange — no raw JDK
 * {@code HttpClient} needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacebookOAuthServiceImpl implements FacebookOAuthService {

    private static final String FB_EXCHANGE_TOKEN_GRANT = "fb_exchange_token";

    private final FacebookProperties facebookProperties;
    private final FacebookApiClient facebookApiClient;
    private final StateTokenService stateTokenService;

    @Override
    public String buildAuthorizationUrl(UUID userId) {
        String state = stateTokenService.generateState(userId);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", facebookProperties.getAppId());
        params.put("redirect_uri", facebookProperties.getRedirectUri());
        params.put("response_type", "code");
        params.put("scope", facebookProperties.getScope());
        params.put("state", state);

        String queryString = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        return facebookProperties.getAuthUri() + "?" + queryString;
    }

    @Override
    public FacebookTokenResponse exchangeCodeForToken(String authorizationCode) {
        try {
            return facebookApiClient.exchangeCodeForToken(
                    facebookProperties.getAppId(),
                    facebookProperties.getRedirectUri(),
                    facebookProperties.getAppSecret(),
                    authorizationCode);
        } catch (FeignException ex) {
            log.error("Facebook OAuth code exchange failed: HTTP {}", ex.status());
            throw new ExternalApiException("Failed to exchange authorization code with Facebook", ex);
        }
    }

    @Override
    public FacebookTokenResponse exchangeForLongLivedToken(String shortLivedUserToken) {
        try {
            return facebookApiClient.exchangeForLongLivedToken(
                    FB_EXCHANGE_TOKEN_GRANT,
                    facebookProperties.getAppId(),
                    facebookProperties.getAppSecret(),
                    shortLivedUserToken);
        } catch (FeignException ex) {
            log.error("Facebook long-lived token exchange failed: HTTP {}", ex.status());
            throw new ExternalApiException("Failed to exchange for a long-lived Facebook user token", ex);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
