package com.platform.analytics.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 * Shared success/failure redirect for every platform's OAuth {@code
 * /callback} endpoint. Before this existed, a failure (e.g. the account
 * already being connected to another user) propagated as a raw JSON
 * exception response instead of a redirect - the in-app browser never saw
 * the app's custom-scheme redirect URL, so WebBrowser.openAuthSessionAsync
 * on the frontend just timed out/was dismissed, surfacing a generic
 * "Connection was cancelled" with no indication of what actually went
 * wrong. Redirecting with an `error`/`message` query param on failure (the
 * same way success redirects with `connected`/`accountId`) lets the
 * frontend show the real reason.
 */
public final class OAuthCallbackRedirect {

    private OAuthCallbackRedirect() {
    }

    public static void success(HttpServletResponse response, String frontendRedirectUri, String platform, String accountId) {
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader(HttpHeaders.LOCATION,
                frontendRedirectUri + "?connected=" + platform + "&accountId=" + accountId);
    }

    public static void failure(HttpServletResponse response, String frontendRedirectUri, String platform, String message) {
        String safeMessage = message != null && !message.isBlank() ? message : "Could not connect your account.";
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader(HttpHeaders.LOCATION,
                frontendRedirectUri + "?error=" + platform + "&message="
                        + URLEncoder.encode(safeMessage, StandardCharsets.UTF_8));
    }
}
