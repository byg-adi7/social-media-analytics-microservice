package com.audienceinsights.audience_insights_auth_service.Service;

import com.audienceinsights.audience_insights_auth_service.Config.JwtUtil;
import com.audienceinsights.audience_insights_auth_service.Dto.AuthResponse;
import com.audienceinsights.audience_insights_auth_service.Dto.LoginRequest;
import com.audienceinsights.audience_insights_auth_service.Dto.RegisterResponse;
import com.audienceinsights.audience_insights_auth_service.Dto.TokenValidationResponse;
import com.audienceinsights.audience_insights_auth_service.Dto.UserRequest;
import com.audienceinsights.audience_insights_auth_service.Entity.User;
import com.audienceinsights.audience_insights_auth_service.Exception.EmailAlreadyRegisteredException;
import com.audienceinsights.audience_insights_auth_service.Exception.EmailNotVerifiedException;
import com.audienceinsights.audience_insights_auth_service.Exception.InvalidCredentialsException;
import com.audienceinsights.audience_insights_auth_service.Exception.InvalidVerificationTokenException;
import com.audienceinsights.audience_insights_auth_service.Repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final int VERIFICATION_TOKEN_VALID_HOURS = 24;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
    }

    public RegisterResponse register(UserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyRegisteredException("Email already registered");
        }

        String verificationToken = UUID.randomUUID().toString();

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .emailVerified(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiry(LocalDateTime.now().plusHours(VERIFICATION_TOKEN_VALID_HOURS))
                .build();

        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), verificationToken);

        return RegisterResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .emailVerified(false)
                .message("Registered! Check your email to verify your account before logging in.")
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Please verify your email before logging in. Check your inbox for the verification link.");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    /**
     * Validates a raw JWT (without the "Bearer " prefix) and returns the
     * claims embedded at generation time. Called by the Analytics Service
     * (and any other downstream service) on every authenticated request,
     * since authentication is centralized here.
     */
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

    /**
     * Called from the public {@code GET /api/auth/verify-email} link in the
     * email itself - marks the account verified and burns the token so it
     * can't be reused.
     */
    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new InvalidVerificationTokenException("This verification link is invalid."));

        if (user.getVerificationTokenExpiry() == null || user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new InvalidVerificationTokenException("This verification link has expired. Please request a new one.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);
    }

    /**
     * Issues a fresh token and re-sends the email. Idempotent and doesn't
     * leak whether the email is already verified vs. doesn't exist, beyond
     * what registration's 409 already reveals.
     */
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("No account found for this email."));

        if (user.isEmailVerified()) {
            return;
        }

        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(VERIFICATION_TOKEN_VALID_HOURS));
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), verificationToken);
    }
}
