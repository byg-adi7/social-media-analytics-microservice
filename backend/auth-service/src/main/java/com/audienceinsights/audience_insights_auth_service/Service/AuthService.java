package com.audienceinsights.audience_insights_auth_service.Service;

import com.audienceinsights.audience_insights_auth_service.Config.JwtUtil;
import com.audienceinsights.audience_insights_auth_service.Dto.TokenValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final JwtUtil jwtUtil;

    public AuthService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public TokenValidationResponse validate(String token) {
        if (!jwtUtil.isTokenValid(token)) {
            return TokenValidationResponse.builder().valid(false).build();
        }

        try {
            return TokenValidationResponse.builder()
                    .valid(true)
                    .userId(jwtUtil.extractUserId(token))
                    .email(jwtUtil.extractEmail(token))
                    .role(jwtUtil.extractRole(token))
                    .build();
        } catch (Exception e) {
            // Belt-and-braces: isTokenValid() already checks the claims
            // this depends on, so this should be unreachable, but a token
            // this internet-facing endpoint can't parse should never 500 -
            // fail closed instead.
            log.warn("Token passed validity check but claim extraction failed: {}", e.getMessage());
            return TokenValidationResponse.builder().valid(false).build();
        }
    }
}
