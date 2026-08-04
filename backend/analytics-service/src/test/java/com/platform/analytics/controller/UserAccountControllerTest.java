package com.platform.analytics.controller;

import com.platform.analytics.security.AuthenticatedUser;
import com.platform.analytics.service.SupabaseAdminService;
import com.platform.notification.constant.NotificationType;
import com.platform.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserAccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class UserAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SupabaseAdminService supabaseAdminService;

    @MockBean
    private NotificationService notificationService;

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
    void deleteMyAccount_sendsNotificationBeforeDeletingUser() throws Exception {
        // Regression test: the ACCOUNT_DELETED push must be sent before
        // supabaseAdminService.deleteUser() runs, not after - the
        // user-deleted webhook that follows cascade-deletes this user's
        // device tokens, so a notification sent afterward would have
        // nowhere to be pushed to.
        mockMvc.perform(delete("/api/users/me"))
                .andExpect(status().isNoContent());

        InOrder order = inOrder(notificationService, supabaseAdminService);
        order.verify(notificationService).notifyUser(eq(userId), eq(NotificationType.ACCOUNT_DELETED), any(), any(), any());
        order.verify(supabaseAdminService).deleteUser(userId);
    }
}
