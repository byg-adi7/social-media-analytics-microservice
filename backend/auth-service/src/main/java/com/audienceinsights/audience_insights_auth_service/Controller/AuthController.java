package com.audienceinsights.audience_insights_auth_service.Controller;

import com.audienceinsights.audience_insights_auth_service.Dto.AuthResponse;
import com.audienceinsights.audience_insights_auth_service.Dto.LoginRequest;
import com.audienceinsights.audience_insights_auth_service.Dto.UserRequest;
import com.audienceinsights.audience_insights_auth_service.Service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
    @GetMapping("/test")
public String test() {
    return "Controller is working!";
}
}
