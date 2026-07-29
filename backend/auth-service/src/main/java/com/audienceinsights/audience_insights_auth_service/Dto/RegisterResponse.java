package com.audienceinsights.audience_insights_auth_service.Dto;

/**
 * Response for {@code POST /api/auth/register}. Deliberately has no {@code token}
 * field — registration no longer starts an authenticated session, since the
 * account must be email-verified before login succeeds.
 */
public class RegisterResponse {

    private String username;
    private String email;
    private boolean emailVerified;
    private String message;

    public RegisterResponse() {}

    private RegisterResponse(Builder builder) {
        this.username = builder.username;
        this.email = builder.email;
        this.emailVerified = builder.emailVerified;
        this.message = builder.message;
    }

    public static Builder builder() { return new Builder(); }

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getMessage() { return message; }

    public static class Builder {
        private String username;
        private String email;
        private boolean emailVerified;
        private String message;

        public Builder username(String username) { this.username = username; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder emailVerified(boolean emailVerified) { this.emailVerified = emailVerified; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public RegisterResponse build() { return new RegisterResponse(this); }
    }
}
