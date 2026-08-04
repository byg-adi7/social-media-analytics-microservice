package com.platform.analytics.spotify;

import com.platform.analytics.dto.response.AuthorizationUrlResponse;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.security.SecurityContextUtil;
import com.platform.analytics.spotify.service.SpotifyConnectionService;
import com.platform.analytics.util.OAuthCallbackRedirect;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

/**
 * Real OAuth 2.0 connect flow for Spotify.
 * <p>
 * {@code /authorize} is called by the authenticated frontend to obtain the
 * Spotify consent-screen URL. {@code /callback} is where Spotify redirects
 * the user's raw browser back to — it is intentionally public (see
 * {@link com.platform.analytics.config.SecurityConfig}) since that request
 * carries no {@code Authorization} header; the user is instead identified
 * via the signed {@code state} parameter (see
 * {@link com.platform.analytics.security.StateTokenService}).
 */
@Slf4j
@RestController
@RequestMapping("/api/oauth/spotify")
@RequiredArgsConstructor
@Tag(name = "Spotify OAuth", description = "Real Spotify OAuth 2.0 flow for connecting a Spotify account")
public class SpotifyOAuthController {

    private final SpotifyConnectionService spotifyConnectionService;
    private final SpotifyProperties spotifyProperties;

    @GetMapping("/authorize")
    @Operation(summary = "Get the Spotify consent-screen URL to connect a Spotify account",
            description = "Requires authentication. The frontend should redirect the user's browser to the returned URL.")
    public AuthorizationUrlResponse authorize() {
        log.info("Incoming request: build Spotify OAuth authorization URL");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        String url = spotifyConnectionService.getAuthorizationUrl(userId);
        return AuthorizationUrlResponse.builder().authorizationUrl(url).build();
    }

    @GetMapping("/callback")
    @Operation(summary = "OAuth callback invoked by Spotify after the user grants/denies consent",
            description = "Public endpoint — identifies the user via the signed 'state' parameter, not a JWT. " +
                    "On success, redirects the browser to the configured frontend URL.")
    public void callback(@RequestParam("code") String code,
                          @RequestParam("state") String state,
                          HttpServletResponse response) throws IOException {
        log.info("Incoming request: Spotify OAuth callback");

        try {
            SocialAccountResponse account = spotifyConnectionService.completeConnection(code, state);
            log.info("Spotify account {} connected successfully, redirecting to frontend", account.getId());
            OAuthCallbackRedirect.success(response, spotifyProperties.getFrontendRedirectUri(), "spotify", account.getId().toString());
        } catch (Exception ex) {
            log.warn("Spotify OAuth callback failed: {}", ex.getMessage());
            OAuthCallbackRedirect.failure(response, spotifyProperties.getFrontendRedirectUri(), "spotify", ex.getMessage());
        }
    }
}
