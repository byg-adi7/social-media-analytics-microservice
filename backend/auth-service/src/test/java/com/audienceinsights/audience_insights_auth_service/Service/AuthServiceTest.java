package com.audienceinsights.audience_insights_auth_service.Service;

import com.audienceinsights.audience_insights_auth_service.Config.JwtUtil;
import com.audienceinsights.audience_insights_auth_service.Dto.AuthResponse;
import com.audienceinsights.audience_insights_auth_service.Dto.LoginRequest;
import com.audienceinsights.audience_insights_auth_service.Dto.TokenValidationResponse;
import com.audienceinsights.audience_insights_auth_service.Dto.UserRequest;
import com.audienceinsights.audience_insights_auth_service.Entity.User;
import com.audienceinsights.audience_insights_auth_service.Exception.EmailAlreadyRegisteredException;
import com.audienceinsights.audience_insights_auth_service.Exception.InvalidCredentialsException;
import com.audienceinsights.audience_insights_auth_service.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

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
    void register_createsUser_hashesPassword_andReturnsToken() {
        UserRequest request = new UserRequest();
        request.setUsername("newuser");
        request.setEmail("new@example.com");
        request.setPassword("plaintext-password");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintext-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(userId);
            return u;
        });
        when(jwtUtil.generateToken(eq(userId), eq("new@example.com"), eq("USER"))).thenReturn("a.jwt.token");

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("a.jwt.token");
        assertThat(response.getUsername()).isEqualTo("newuser");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getRole()).isEqualTo("USER");

        // The password must never reach the repository/token layer in
        // plaintext - only the encoder's output should be persisted.
        verify(passwordEncoder).encode("plaintext-password");
        verify(userRepository).save(any(User.class));
        verify(userRepository, never()).save(argThat(u -> "plaintext-password".equals(u.getPassword())));
    }

    @Test
    void register_throwsConflict_whenEmailAlreadyRegistered() {
        UserRequest request = new UserRequest();
        request.setUsername("dupe");
        request.setEmail("existing@example.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
        verify(jwtUtil, never()).generateToken(any(), anyString(), anyString());
    }

    @Test
    void login_returnsToken_whenCredentialsAreValid() {
        User user = User.builder()
                .username("existinguser")
                .email("existing@example.com")
                .password("hashed-password")
                .role("USER")
                .build();
        user.setId(userId);

        LoginRequest request = new LoginRequest();
        request.setEmail("existing@example.com");
        request.setPassword("correct-password");

        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(jwtUtil.generateToken(userId, "existing@example.com", "USER")).thenReturn("a.jwt.token");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("a.jwt.token");
        assertThat(response.getEmail()).isEqualTo("existing@example.com");
    }

    @Test
    void login_throwsUnauthorized_whenPasswordIsWrong() {
        User user = User.builder()
                .username("existinguser")
                .email("existing@example.com")
                .password("hashed-password")
                .role("USER")
                .build();
        user.setId(userId);

        LoginRequest request = new LoginRequest();
        request.setEmail("existing@example.com");
        request.setPassword("wrong-password");

        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");

        verify(jwtUtil, never()).generateToken(any(), anyString(), anyString());
    }

    @Test
    void login_throwsUnauthorized_whenEmailDoesNotExist() {
        LoginRequest request = new LoginRequest();
        request.setEmail("nobody@example.com");
        request.setPassword("whatever");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);

        // Must not leak whether the account exists via a different code
        // path/timing-sensitive branch - passwordEncoder should never even
        // be consulted when there's no user to compare against.
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void validate_returnsValidTrueWithClaims_forAValidToken() {
        when(jwtUtil.isTokenValid("good-token")).thenReturn(true);
        when(jwtUtil.extractUserId("good-token")).thenReturn(userId);
        when(jwtUtil.extractEmail("good-token")).thenReturn("existing@example.com");
        when(jwtUtil.extractRole("good-token")).thenReturn("USER");

        TokenValidationResponse response = authService.validate("good-token");

        assertThat(response.isValid()).isTrue();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("existing@example.com");
        assertThat(response.getRole()).isEqualTo("USER");
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
