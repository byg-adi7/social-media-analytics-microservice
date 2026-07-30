package com.platform.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.request.ConnectAccountRequest;
import com.platform.analytics.dto.response.SocialAccountResponse;
import com.platform.analytics.security.AuthenticatedUser;
import com.platform.analytics.service.SocialAccountService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SocialAccountService socialAccountService;

    // JwtAuthenticationFilter (a Filter, so included in the @WebMvcTest
    // slice) depends on JwtUtil, which needs a resolvable
    // supabase.jwt-secret property this slice doesn't load - mock it
    // instead (addFilters=false means the filter never actually runs
    // against these requests anyway).
    @MockBean
    private com.platform.analytics.security.JwtUtil jwtUtil;

    private UUID userId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();

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
    void connectAccount_returns201_withCreatedAccount() throws Exception {
        ConnectAccountRequest request = ConnectAccountRequest.builder()
                .platform(Platform.YOUTUBE)
                .accountId("yt-123")
                .accountName("My Channel")
                .accessToken("token-abc")
                .build();

        SocialAccountResponse response = SocialAccountResponse.builder()
                .id(accountId)
                .platform(Platform.YOUTUBE)
                .accountName("My Channel")
                .active(true)
                .connectedAt(LocalDateTime.now())
                .build();

        when(socialAccountService.connectAccount(eq(userId), any())).thenReturn(response);

        mockMvc.perform(post("/api/accounts/connect")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.platform").value("YOUTUBE"));
    }

    @Test
    void connectAccount_returns400_whenAccountIdMissing() throws Exception {
        ConnectAccountRequest request = ConnectAccountRequest.builder()
                .platform(Platform.YOUTUBE)
                .accountName("My Channel")
                .accessToken("token-abc")
                .build();

        mockMvc.perform(post("/api/accounts/connect")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void getAccounts_returnsListOfAccounts() throws Exception {
        SocialAccountResponse response = SocialAccountResponse.builder()
                .id(accountId).platform(Platform.INSTAGRAM).accountName("My IG").active(true).build();

        when(socialAccountService.getAccounts(userId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].platform").value("INSTAGRAM"));
    }

    @Test
    void disconnectAccount_returns204() throws Exception {
        mockMvc.perform(delete("/api/accounts/{id}", accountId))
                .andExpect(status().isNoContent());
    }
}
