package com.platform.analytics.controller;

import com.platform.analytics.client.AuthServiceClient;
import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.TokenValidationResponse;
import com.platform.analytics.entity.Analytics;
import com.platform.analytics.entity.SocialAccount;
import com.platform.analytics.repository.AnalyticsRepository;
import com.platform.analytics.repository.SocialAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for a real production bug: every endpoint that reads
 * Analytics.getSocialAccount() after a repository call returns (dashboard,
 * platform-comparison, engagement-distribution, engagement/followers/views
 * charts) used to throw LazyInitializationException as soon as any row
 * existed, because socialAccount is a LAZY @ManyToOne and open-in-view is
 * disabled - the session closes when the repository method returns, before
 * the service layer traverses the association. Neither @DataJpaTest (whose
 * own transaction keeps the session open all test long, masking the bug)
 * nor @WebMvcTest (which mocks the service layer entirely) can catch this
 * class of bug - only a real end-to-end request against a real datasource
 * can, which is what this test is for. Fixed by adding JOIN FETCH
 * a.socialAccount to the underlying queries in AnalyticsRepository.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private AnalyticsRepository analyticsRepository;

    @MockBean
    private AuthServiceClient authServiceClient;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        // accountId must be unique per test method: uk_platform_account_id is
        // a global constraint and @SpringBootTest doesn't roll back between
        // tests in this class the way @DataJpaTest does.
        SocialAccount account = socialAccountRepository.save(SocialAccount.builder()
                .userId(userId)
                .platform(Platform.YOUTUBE)
                .accountId("yt-dash-" + userId)
                .accountName("Dashboard Test Channel")
                .connectedAt(LocalDateTime.now())
                .active(true)
                .build());

        analyticsRepository.save(Analytics.builder()
                .socialAccount(account)
                .analyticsDate(LocalDate.now())
                .followers(1000)
                .views(500)
                .likes(50)
                .comments(5)
                .shares(2)
                .reach(400)
                .impressions(600)
                .engagementRate(5.7)
                .build());

        when(authServiceClient.validateToken(any())).thenReturn(TokenValidationResponse.builder()
                .valid(true)
                .userId(userId)
                .email("dashboard-test@example.com")
                .role("authenticated")
                .build());
    }

    @Test
    void dashboard_withRealConnectedAccountData_returnsAggregatedKpisWithoutLazyInitializationException() throws Exception {
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer fake-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFollowers").value(1000))
                .andExpect(jsonPath("$.bestPerformingPlatform").value("YOUTUBE"));
    }

    @Test
    void platformComparisonChart_withRealData_returnsWithoutLazyInitializationException() throws Exception {
        mockMvc.perform(get("/api/charts/platform-comparison").header("Authorization", "Bearer fake-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].platform").value("YOUTUBE"))
                .andExpect(jsonPath("$[0].followers").value(1000));
    }

    @Test
    void engagementChart_withRealData_returnsWithoutLazyInitializationException() throws Exception {
        mockMvc.perform(get("/api/charts/engagement").header("Authorization", "Bearer fake-token"))
                .andExpect(status().isOk());
    }
}
