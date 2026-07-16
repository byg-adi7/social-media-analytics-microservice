package com.platform.analytics.repository;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.entity.Analytics;
import com.platform.analytics.entity.SocialAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AnalyticsRepositoryTest {

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private AnalyticsRepository analyticsRepository;

    private UUID userId;
    private SocialAccount account;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        account = socialAccountRepository.save(SocialAccount.builder()
                .userId(userId)
                .platform(Platform.YOUTUBE)
                .accountId("yt-abc")
                .accountName("Test Channel")
                .connectedAt(LocalDateTime.now())
                .active(true)
                .build());
    }

    @Test
    void save_and_findBySocialAccountIdAndAnalyticsDate_returnsRecord() {
        Analytics analytics = Analytics.builder()
                .socialAccount(account)
                .analyticsDate(LocalDate.now())
                .followers(1000)
                .likes(50)
                .comments(10)
                .shares(5)
                .views(2000)
                .engagementRate(6.5)
                .build();

        analyticsRepository.save(analytics);

        Optional<Analytics> found = analyticsRepository.findBySocialAccountIdAndAnalyticsDate(account.getId(), LocalDate.now());

        assertThat(found).isPresent();
        assertThat(found.get().getFollowers()).isEqualTo(1000);
    }

    @Test
    void findAllByUserIdAndDateRange_returnsOnlyMatchingRecords() {
        LocalDate today = LocalDate.now();
        analyticsRepository.save(Analytics.builder()
                .socialAccount(account).analyticsDate(today.minusDays(1)).followers(900).build());
        analyticsRepository.save(Analytics.builder()
                .socialAccount(account).analyticsDate(today).followers(1000).build());
        analyticsRepository.save(Analytics.builder()
                .socialAccount(account).analyticsDate(today.minusDays(10)).followers(500).build());

        List<Analytics> result = analyticsRepository.findAllByUserIdAndDateRange(
                userId, today.minusDays(2), today);

        assertThat(result).hasSize(2);
    }

    @Test
    void existsByUserIdAndPlatformAndAccountId_detectsDuplicateConnection() {
        boolean exists = socialAccountRepository.existsByUserIdAndPlatformAndAccountId(
                userId, Platform.YOUTUBE, "yt-abc");

        assertThat(exists).isTrue();
    }

    @Test
    void findAllByUserIdAndActiveTrue_excludesInactiveAccounts() {
        socialAccountRepository.save(SocialAccount.builder()
                .userId(userId)
                .platform(Platform.TIKTOK)
                .accountId("tt-xyz")
                .accountName("Inactive")
                .connectedAt(LocalDateTime.now())
                .active(false)
                .build());

        List<SocialAccount> activeAccounts = socialAccountRepository.findAllByUserIdAndActiveTrue(userId);

        assertThat(activeAccounts).hasSize(1);
        assertThat(activeAccounts.get(0).getPlatform()).isEqualTo(Platform.YOUTUBE);
    }
}
