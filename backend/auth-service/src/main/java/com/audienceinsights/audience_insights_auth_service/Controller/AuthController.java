package com.audienceinsights.audience_insights_auth_service.Controller;

import com.audienceinsights.audience_insights_auth_service.Dto.AuthResponse;
import com.audienceinsights.audience_insights_auth_service.Dto.LoginRequest;
import com.audienceinsights.audience_insights_auth_service.Dto.RegisterResponse;
import com.audienceinsights.audience_insights_auth_service.Dto.ResendVerificationRequest;
import com.audienceinsights.audience_insights_auth_service.Dto.TokenValidationResponse;
import com.audienceinsights.audience_insights_auth_service.Dto.UserRequest;
import com.audienceinsights.audience_insights_auth_service.Exception.InvalidVerificationTokenException;
import com.audienceinsights.audience_insights_auth_service.Service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Called by downstream services (e.g. the Analytics Service's
     * AuthServiceClient) on every authenticated request. Accepts the raw
     * "Authorization" header value and reports whether the bearer token is
     * valid, plus the identity claims embedded in it.
     */
    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return ResponseEntity.ok(authService.validate(token));
    }

    /**
     * Public link opened directly from the verification email - deliberately
     * returns a plain HTML page (not JSON) since it's tapped from an email
     * client / browser, not called by the frontend app.
     */
    @GetMapping(value = "/verify-email", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok(htmlPage(
                    "Email verified",
                    "Your email has been verified. You can now log in from the Audience Insights app."));
        } catch (InvalidVerificationTokenException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(htmlPage("Verification failed", ex.getMessage()));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/test")
    public String test() {
        return "Controller is working!";
    }

    private String htmlPage(String title, String message) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + title + "</title>" +
                "<style>body{font-family:sans-serif;background:#0f172a;color:#fff;display:flex;" +
                "align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;padding:24px;box-sizing:border-box;}" +
                "div{max-width:420px}h1{color:#38bdf8}</style></head>" +
                "<body><div><h1>" + title + "</h1><p>" + message + "</p></div></body></html>";
    }
}
