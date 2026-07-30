package com.platform.analytics.controller;

import com.platform.analytics.config.WebhookProperties;
import com.platform.analytics.service.UserDataCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class WebhookControllerTest {

    private static final String CORRECT_SECRET = "correct-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDataCleanupService userDataCleanupService;

    @MockBean
    private WebhookProperties webhookProperties;

    // Filters present in the @WebMvcTest slice for WebhookController but
    // not exercised by these tests (addFilters=false) - mocked purely so
    // the application context loads, same reasoning as AccountControllerTest.
    @MockBean
    private com.platform.analytics.security.JwtUtil jwtUtil;

    @MockBean
    private com.platform.analytics.security.RateLimitFilter rateLimitFilter;

    @Test
    void userDeleted_validSecret_deletesUserDataAndReturns204() throws Exception {
        when(webhookProperties.getUserDeletionSecret()).thenReturn(CORRECT_SECRET);
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/webhooks/user-deleted")
                        .header("X-Webhook-Secret", CORRECT_SECRET)
                        .contentType("application/json")
                        .content("{\"type\":\"DELETE\",\"table\":\"users\",\"schema\":\"auth\","
                                + "\"old_record\":{\"id\":\"" + userId + "\"}}"))
                .andExpect(status().isNoContent());

        verify(userDataCleanupService).deleteAllDataForUser(userId);
    }

    @Test
    void userDeleted_wrongSecret_returns401_andSkipsCleanup() throws Exception {
        when(webhookProperties.getUserDeletionSecret()).thenReturn(CORRECT_SECRET);

        mockMvc.perform(post("/api/webhooks/user-deleted")
                        .header("X-Webhook-Secret", "wrong-secret")
                        .contentType("application/json")
                        .content("{\"type\":\"DELETE\",\"table\":\"users\",\"schema\":\"auth\","
                                + "\"old_record\":{\"id\":\"" + UUID.randomUUID() + "\"}}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDataCleanupService);
    }

    @Test
    void userDeleted_missingSecretHeader_returns400_notServerError() throws Exception {
        // Regression coverage for GlobalExceptionHandler's
        // MissingRequestHeaderException handler, added alongside this
        // endpoint - a missing @RequestHeader used to fall through to the
        // generic Exception handler and return 500 instead of 400.
        mockMvc.perform(post("/api/webhooks/user-deleted")
                        .contentType("application/json")
                        .content("{\"type\":\"DELETE\",\"table\":\"users\",\"schema\":\"auth\","
                                + "\"old_record\":{\"id\":\"" + UUID.randomUUID() + "\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.errorCode").value("BAD_REQUEST"));

        verifyNoInteractions(userDataCleanupService);
    }

    @Test
    void userDeleted_missingOldRecord_returns400() throws Exception {
        when(webhookProperties.getUserDeletionSecret()).thenReturn(CORRECT_SECRET);

        mockMvc.perform(post("/api/webhooks/user-deleted")
                        .header("X-Webhook-Secret", CORRECT_SECRET)
                        .contentType("application/json")
                        .content("{\"type\":\"DELETE\",\"table\":\"users\",\"schema\":\"auth\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userDataCleanupService);
    }
}
