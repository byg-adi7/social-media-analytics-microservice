package com.platform.analytics.instagram;

import com.platform.analytics.dto.response.AuthorizationUrlResponse;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.instagram.service.InstagramConnectionService;
import com.platform.analytics.security.SecurityContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

/**
 * Real OAuth 2.0 connect flow for Instagram (Business Login for Instagram).
 * <p>
 * {@code /authorize} is called by the authenticated frontend to obtain the
 * Instagram consent-screen URL. {@code /callback} is where Instagram
 * redirects the user's raw browser back to — it is intentionally public
 * (see {@link com.platform.analytics.config.SecurityConfig}) since that
 * request carries no {@code Authorization} header; the user is instead
 * identified via the signed {@code state} parameter (see
 * {@link com.platform.analytics.security.StateTokenService}).
 */
@Slf4j
@RestController
@RequestMapping("/api/oauth/instagram")
@RequiredArgsConstructor
@Tag(name = "Instagram OAuth", description = "Real Instagram OAuth 2.0 flow for connecting an Instagram Business/Creator account")
public class InstagramOAuthController {

    private final InstagramConnectionService instagramConnectionService;
    private final InstagramProperties instagramProperties;

    @GetMapping("/authorize")
    @Operation(summary = "Get the Instagram consent-screen URL to connect an Instagram account",
            description = "Requires authentication. The frontend should redirect the user's browser to the returned URL.")
    public AuthorizationUrlResponse authorize() {
        log.info("Incoming request: build Instagram OAuth authorization URL");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        String url = instagramConnectionService.getAuthorizationUrl(userId);
        return AuthorizationUrlResponse.builder().authorizationUrl(url).build();
    }

    @GetMapping("/callback")
    @Operation(summary = "OAuth callback invoked by Instagram after the user grants/denies consent",
            description = "Public endpoint — identifies the user via the signed 'state' parameter, not a JWT. " +
                    "On success, redirects the browser to the configured frontend URL.")
    public void callback(@RequestParam("code") String code,
                          @RequestParam("state") String state,
                          HttpServletResponse response) throws IOException {
        log.info("Incoming request: Instagram OAuth callback");

        SocialAccountResponse account = instagramConnectionService.completeConnection(code, state);

        log.info("Instagram account {} connected successfully, redirecting to frontend", account.getId());

        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader(HttpHeaders.LOCATION,
                instagramProperties.getFrontendRedirectUri() + "?connected=instagram&accountId=" + account.getId());
    }
}
