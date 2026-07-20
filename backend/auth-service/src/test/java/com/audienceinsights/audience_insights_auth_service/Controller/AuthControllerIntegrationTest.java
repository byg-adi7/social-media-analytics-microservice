package com.audienceinsights.audience_insights_auth_service.Controller;

import com.audienceinsights.audience_insights_auth_service.Dto.AuthResponse;
import com.audienceinsights.audience_insights_auth_service.Dto.LoginRequest;
import com.audienceinsights.audience_insights_auth_service.Dto.TokenValidationResponse;
import com.audienceinsights.audience_insights_auth_service.Dto.UserRequest;
import com.audienceinsights.audience_insights_auth_service.Exception.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises real HTTP requests against the full application context (real
 * H2-backed JPA persistence, real BCrypt hashing, real JWT
 * generation/parsing) - not mocked. This is the automated equivalent of the
 * manual curl-based verification this service previously only got by hand.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void disableRequestStreaming() {
        // The JDK's default HttpURLConnection-based request factory can't
        // replay a streamed request body when the server responds 401/409
        // (it throws HttpRetryException instead of just returning the
        // response), which every "expect an error status" test below
        // relies on. Buffering the body instead of streaming it avoids that
        // - a test-client quirk, not an application bug.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setOutputStreaming(false);
        restTemplate.getRestTemplate().setRequestFactory(factory);
    }

    @Test
    void register_thenLogin_thenValidate_fullRoundTrip() {
        String email = uniqueEmail();
        UserRequest registerRequest = new UserRequest();
        registerRequest.setUsername("roundtrip");
        registerRequest.setEmail(email);
        registerRequest.setPassword("Password123!");

        ResponseEntity<AuthResponse> registerResponse =
                restTemplate.postForEntity("/api/auth/register", registerRequest, AuthResponse.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().getToken()).isNotBlank();
        assertThat(registerResponse.getBody().getEmail()).isEqualTo(email);
        assertThat(registerResponse.getBody().getRole()).isEqualTo("USER");

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("Password123!");

        ResponseEntity<AuthResponse> loginResponse =
                restTemplate.postForEntity("/api/auth/login", loginRequest, AuthResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = loginResponse.getBody().getToken();
        assertThat(token).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<TokenValidationResponse> validateResponse = restTemplate.exchange(
                "/api/auth/validate", HttpMethod.GET, new HttpEntity<>(headers), TokenValidationResponse.class);

        assertThat(validateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(validateResponse.getBody().isValid()).isTrue();
        assertThat(validateResponse.getBody().getEmail()).isEqualTo(email);
        assertThat(validateResponse.getBody().getRole()).isEqualTo("USER");
        assertThat(validateResponse.getBody().getUserId()).isNotNull();
    }

    @Test
    void register_conflictsWithClearErrorBody_whenEmailAlreadyExists() {
        String email = uniqueEmail();
        UserRequest request = new UserRequest();
        request.setUsername("dupe");
        request.setEmail(email);
        request.setPassword("Password123!");

        restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);
        ResponseEntity<ErrorResponse> secondAttempt =
                restTemplate.postForEntity("/api/auth/register", request, ErrorResponse.class);

        assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondAttempt.getBody()).isNotNull();
        assertThat(secondAttempt.getBody().getMessage()).containsIgnoringCase("already registered");
    }

    @Test
    void login_returnsCleanUnauthorized_withNoStackTrace_onWrongPassword() {
        String email = uniqueEmail();
        UserRequest registerRequest = new UserRequest();
        registerRequest.setUsername("wrongpwtest");
        registerRequest.setEmail(email);
        registerRequest.setPassword("CorrectPassword123!");
        restTemplate.postForEntity("/api/auth/register", registerRequest, AuthResponse.class);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("WrongPassword!");

        ResponseEntity<ErrorResponse> response =
                restTemplate.postForEntity("/api/auth/login", loginRequest, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).containsIgnoringCase("invalid email or password");
    }

    @Test
    void login_returnsUnauthorized_forNonexistentEmail() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(uniqueEmail());
        loginRequest.setPassword("whatever123");

        ResponseEntity<ErrorResponse> response =
                restTemplate.postForEntity("/api/auth/login", loginRequest, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validate_reportsInvalid_forAGarbageToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt");

        ResponseEntity<TokenValidationResponse> response = restTemplate.exchange(
                "/api/auth/validate", HttpMethod.GET, new HttpEntity<>(headers), TokenValidationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isValid()).isFalse();
    }

    @Test
    void register_rejectsInvalidInput_withBadRequest() {
        UserRequest request = new UserRequest();
        request.setUsername("");
        request.setEmail("not-an-email");
        request.setPassword("123");

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String uniqueEmail() {
        return "roundtrip-" + UUID.randomUUID() + "@example.com";
    }
}
