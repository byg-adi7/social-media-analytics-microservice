package com.platform.analytics.facebook;

import com.platform.analytics.dto.response.AuthorizationUrlResponse;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.facebook.service.FacebookConnectionService;
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
 * Real OAuth 2.0 connect flow for Facebook (Facebook Login, Page-scoped).
 * <p>
 * {@code /authorize} is called by the authenticated frontend to obtain the
 * Facebook consent-screen URL. {@code /callback} is where Facebook
 * redirects the user's raw browser back to — it is intentionally public
 * (see {@link com.platform.analytics.config.SecurityConfig}) since that
 * request carries no {@code Authorization} header; the user is instead
 * identified via the signed {@code state} parameter (see
 * {@link com.platform.analytics.security.StateTokenService}).
 */
@Slf4j
@RestController
@RequestMapping("/api/oauth/facebook")
@RequiredArgsConstructor
@Tag(name = "Facebook OAuth", description = "Real Facebook Login OAuth 2.0 flow for connecting a Facebook Page")
public class FacebookOAuthController {

    private final FacebookConnectionService facebookConnectionService;
    private final FacebookProperties facebookProperties;

    @GetMapping("/authorize")
    @Operation(summary = "Get the Facebook consent-screen URL to connect a Facebook Page",
            description = "Requires authentication. The frontend should redirect the user's browser to the returned URL.")
    public AuthorizationUrlResponse authorize() {
        log.info("Incoming request: build Facebook OAuth authorization URL");
        UUID userId = SecurityContextUtil.getCurrentUserId();
        String url = facebookConnectionService.getAuthorizationUrl(userId);
        return AuthorizationUrlResponse.builder().authorizationUrl(url).build();
    }

    @GetMapping("/callback")
    @Operation(summary = "OAuth callback invoked by Facebook after the user grants/denies consent",
            description = "Public endpoint — identifies the user via the signed 'state' parameter, not a JWT. " +
                    "On success, redirects the browser to the configured frontend URL.")
    public void callback(@RequestParam("code") String code,
                          @RequestParam("state") String state,
                          HttpServletResponse response) throws IOException {
        log.info("Incoming request: Facebook OAuth callback");

        SocialAccountResponse account = facebookConnectionService.completeConnection(code, state);

        log.info("Facebook Page {} connected successfully, redirecting to frontend", account.getId());

        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader(HttpHeaders.LOCATION,
                facebookProperties.getFrontendRedirectUri() + "?connected=facebook&accountId=" + account.getId());
    }
}
