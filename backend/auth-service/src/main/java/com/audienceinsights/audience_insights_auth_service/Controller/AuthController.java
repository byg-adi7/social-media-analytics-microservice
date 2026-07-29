package com.audienceinsights.audience_insights_auth_service.Controller;

import com.audienceinsights.audience_insights_auth_service.Dto.TokenValidationResponse;
import com.audienceinsights.audience_insights_auth_service.Service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Called by downstream services (e.g. the Analytics Service's
     * AuthServiceClient) on every authenticated request. Accepts the raw
     * "Authorization" header value and reports whether the Supabase-issued
     * bearer token is valid, plus the identity claims embedded in it.
     */
    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return ResponseEntity.ok(authService.validate(token));
    }
}
