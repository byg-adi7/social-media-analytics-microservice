package com.platform.analytics.client;

import com.platform.analytics.constant.Platform;
import com.platform.analytics.dto.response.TopContentResponse;
import com.platform.analytics.entity.SocialAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates realistic, deterministic-per-account synthetic analytics data
 * for YouTube, Instagram, TikTok and Facebook. This stands in for real
 * platform API integrations, which can be added later by implementing
 * {@link SocialMediaClient} and registering the bean — everything else in
 * the service (sync scheduler, calculations, controllers) is unaffected.
 */
@Slf4j
@Component
public class MockSocialMediaClient implements SocialMediaClient {

    private static final List<String> CONTENT_TITLES = List.of(
            "Behind the Scenes", "Day in My Life", "Top 10 Tips", "Q&A Session",
            "Product Review", "Tutorial Walkthrough", "Weekly Recap", "Collab Special",
            "Unboxing", "Live Highlights"
    );

    @Override
    public boolean supports(Platform platform) {
        // The mock client supports every currently defined platform.
        return platform != null;
    }

    @Override
    public DailyMetrics fetchDailyMetrics(SocialAccount account, LocalDate date) {
        Random random = seededRandom(account, date);

        long baseFollowers = baseFollowersFor(account.getPlatform());
        long daysSinceConnected = Math.max(0,
                ChronoUnit.DAYS.between(account.getConnectedAt().toLocalDate(), date));

        // Simulate gradual organic growth with daily noise.
        long followers = baseFollowers + (long) (daysSinceConnected * (50 + random.nextInt(150)));
        long following = 100L + random.nextInt(500);

        long views = 500L + random.nextInt(5000);
        long impressions = views + random.nextInt(3000);
        long reach = (long) (impressions * (0.6 + random.nextDouble() * 0.3));
        long profileVisits = 20L + random.nextInt(300);

        double watchTime = round2(views * (0.3 + random.nextDouble() * 2.5)); // minutes

        long likes = (long) (views * (0.03 + random.nextDouble() * 0.07));
        long comments = (long) (views * (0.002 + random.nextDouble() * 0.01));
        long shares = (long) (views * (0.001 + random.nextDouble() * 0.008));
        long saves = (long) (views * (0.001 + random.nextDouble() * 0.006));
        long posts = random.nextInt(3);

        log.debug("Generated mock daily metrics for account={} platform={} date={}",
                account.getId(), account.getPlatform(), date);

        return new DailyMetrics(followers, following, impressions, reach, profileVisits,
                views, watchTime, likes, comments, shares, saves, posts);
    }

    @Override
    public List<TopContentResponse> fetchTopContent(SocialAccount account, int limit) {
        Random random = seededRandom(account, LocalDate.now());
        List<TopContentResponse> content = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            long views = 1000L + random.nextInt(20000);
            long likes = (long) (views * (0.03 + random.nextDouble() * 0.08));
            long comments = (long) (views * (0.002 + random.nextDouble() * 0.01));
            long shares = (long) (views * (0.001 + random.nextDouble() * 0.008));
            double engagementRate = round2((likes + comments + shares) * 100.0 / Math.max(1, views));

            content.add(TopContentResponse.builder()
                    .platform(account.getPlatform())
                    .title(CONTENT_TITLES.get(random.nextInt(CONTENT_TITLES.size())))
                    .publishedDate(LocalDate.now().minusDays(random.nextInt(30)))
                    .views(views)
                    .likes(likes)
                    .comments(comments)
                    .shares(shares)
                    .engagementRate(engagementRate)
                    .build());
        }

        content.sort((a, b) -> Long.compare(b.getViews(), a.getViews()));
        return content;
    }

    private long baseFollowersFor(Platform platform) {
        return switch (platform) {
            case YOUTUBE -> 45_000L;
            case INSTAGRAM -> 52_000L;
            case TIKTOK -> 38_000L;
            case FACEBOOK -> 21_000L;
            case SPOTIFY -> 15_000L;
        };
    }

    /**
     * Seeds the random generator deterministically per account+date so that
     * repeated syncs for the same day are idempotent/consistent rather than
     * producing wildly different numbers on every call.
     */
    private Random seededRandom(SocialAccount account, LocalDate date) {
        long seed = account.getId().hashCode() * 31L + date.toEpochDay();
        return new Random(seed);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
