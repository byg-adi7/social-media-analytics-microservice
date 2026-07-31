package com.platform.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analytics.AnalyticsServiceApplication;
import com.platform.notification.constant.DevicePlatform;
import com.platform.notification.dto.request.RegisterDeviceRequest;
import com.platform.notification.dto.response.DeviceTokenResponse;
import com.platform.analytics.security.AuthenticatedUser;
import com.platform.notification.service.DeviceTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DeviceController lives in com.platform.notification.controller, a sibling
// of com.platform.analytics (where AnalyticsServiceApplication lives) rather
// than a sub-package of it - @WebMvcTest's @SpringBootConfiguration
// auto-discovery only walks up the test class's OWN package tree, so it
// would never find it without this explicit pointer.
@WebMvcTest(controllers = DeviceController.class)
@ContextConfiguration(classes = AnalyticsServiceApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeviceTokenService deviceTokenService;

    // Filters present in this @WebMvcTest slice but not exercised
    // (addFilters=false) - mocked purely so the context loads, same
    // reasoning as AccountControllerTest.
    @MockBean
    private com.platform.analytics.security.JwtUtil jwtUtil;

    @MockBean
    private com.platform.analytics.security.RateLimitFilter rateLimitFilter;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(userId, "creator@example.com", "USER");
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_returns201_withCreatedToken() throws Exception {
        RegisterDeviceRequest request = RegisterDeviceRequest.builder()
                .token("fcm-token-abc").platform(DevicePlatform.ANDROID).build();
        DeviceTokenResponse response = DeviceTokenResponse.builder()
                .id(UUID.randomUUID()).platform(DevicePlatform.ANDROID).active(true)
                .createdAt(LocalDateTime.now()).lastUsedAt(LocalDateTime.now()).build();

        when(deviceTokenService.register(eq(userId), any())).thenReturn(response);

        mockMvc.perform(post("/api/devices/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.platform").value("ANDROID"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void register_missingToken_returns400() throws Exception {
        RegisterDeviceRequest request = RegisterDeviceRequest.builder().platform(DevicePlatform.ANDROID).build();

        mockMvc.perform(post("/api/devices/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void unregister_returns204() throws Exception {
        mockMvc.perform(delete("/api/devices/unregister")
                        .contentType("application/json")
                        .content("{\"token\":\"fcm-token-abc\"}"))
                .andExpect(status().isNoContent());
    }
}
