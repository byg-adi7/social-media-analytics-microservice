package com.audienceinsights.audience_insights_auth_service.Service;

import com.audienceinsights.audience_insights_auth_service.Config.JwtUtil;
import com.audienceinsights.audience_insights_auth_service.Dto.TokenValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void validate_returnsValidTrueWithClaims_forAValidSupabaseToken() {
        when(jwtUtil.isTokenValid("good-token")).thenReturn(true);
        when(jwtUtil.extractUserId("good-token")).thenReturn(userId);
        when(jwtUtil.extractEmail("good-token")).thenReturn("existing@example.com");
        when(jwtUtil.extractRole("good-token")).thenReturn("authenticated");

        TokenValidationResponse response = authService.validate("good-token");

        assertThat(response.isValid()).isTrue();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("existing@example.com");
        assertThat(response.getRole()).isEqualTo("authenticated");
    }

    @Test
    void validate_returnsValidFalse_forAnInvalidToken_withoutExtractingClaims() {
        when(jwtUtil.isTokenValid("bad-token")).thenReturn(false);

        TokenValidationResponse response = authService.validate("bad-token");

        assertThat(response.isValid()).isFalse();
        assertThat(response.getUserId()).isNull();
        verify(jwtUtil, never()).extractUserId(any());
        verify(jwtUtil, never()).extractEmail(any());
        verify(jwtUtil, never()).extractRole(any());
    }
}
