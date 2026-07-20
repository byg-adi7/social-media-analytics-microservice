package com.audienceinsights.audience_insights_auth_service.Dto;

import java.util.UUID;

/**
 * Shape consumed by downstream services (e.g. the Analytics Service's
 * AuthServiceClient/TokenValidationResponse) when validating a JWT issued
 * by this service. Field names must stay in sync with those consumers.
 */
public class TokenValidationResponse {

    private boolean valid;
    private UUID userId;
    private String email;
    private String role;

    public TokenValidationResponse() {}

    private TokenValidationResponse(Builder builder) {
        this.valid = builder.valid;
        this.userId = builder.userId;
        this.email = builder.email;
        this.role = builder.role;
    }

    public static Builder builder() { return new Builder(); }

    public boolean isValid() { return valid; }
    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getRole() { return role; }

    public void setValid(boolean valid) { this.valid = valid; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setEmail(String email) { this.email = email; }
    public void setRole(String role) { this.role = role; }

    public static class Builder {
        private boolean valid;
        private UUID userId;
        private String email;
        private String role;

        public Builder valid(boolean valid) { this.valid = valid; return this; }
        public Builder userId(UUID userId) { this.userId = userId; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder role(String role) { this.role = role; return this; }
        public TokenValidationResponse build() { return new TokenValidationResponse(this); }
    }
}
