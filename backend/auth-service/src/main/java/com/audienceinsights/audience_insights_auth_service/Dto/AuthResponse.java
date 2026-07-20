package com.audienceinsights.audience_insights_auth_service.Dto;

public class AuthResponse {

    private String token;
    private String username;
    private String email;
    private String role;

    public AuthResponse() {}

    private AuthResponse(Builder builder) {
        this.token = builder.token;
        this.username = builder.username;
        this.email = builder.email;
        this.role = builder.role;
    }

    public static Builder builder() { return new Builder(); }

    public String getToken() { return token; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRole() { return role; }

    public static class Builder {
        private String token;
        private String username;
        private String email;
        private String role;

        public Builder token(String token) { this.token = token; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder role(String role) { this.role = role; return this; }
        public AuthResponse build() { return new AuthResponse(this); }
    }
}
