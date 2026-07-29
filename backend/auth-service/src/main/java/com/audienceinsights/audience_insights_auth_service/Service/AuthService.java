package com.audienceinsights.audience_insights_auth_service.Service;

import com.audienceinsights.audience_insights_auth_service.Config.JwtUtil;
import com.audienceinsights.audience_insights_auth_service.Dto.TokenValidationResponse;
import org.springframework.stereotype.Service;

/**
 * Registration, login, and email verification are all handled directly by
 * Supabase Auth now (see the frontend's Supabase client) - this service's
 * only remaining job is validating the Supabase-issued JWT that the
 * frontend attaches to every request, so Analytics/Notification never need
 * to know Supabase exists; they just keep calling this one /validate
 * endpoint exactly as before.
 */
@Service
public class AuthService {

    private final JwtUtil jwtUtil;

    public AuthService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public TokenValidationResponse validate(String token) {
        if (!jwtUtil.isTokenValid(token)) {
            return TokenValidationResponse.builder().valid(false).build();
        }

        return TokenValidationResponse.builder()
                .valid(true)
                .userId(jwtUtil.extractUserId(token))
                .email(jwtUtil.extractEmail(token))
                .role(jwtUtil.extractRole(token))
                .build();
    }
}
