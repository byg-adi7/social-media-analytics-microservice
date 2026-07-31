package com.platform.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the merged Analytics + Notification service.
 * <p>
 * Connects social media accounts, synchronizes analytics data, aggregates
 * metrics, exposes dashboard/report/chart-ready JSON data, and (since the
 * former standalone Notification Service was folded in here) creates
 * in-app notifications and generates on-demand reports. Authentication is
 * validated locally against Supabase's JWT secret (see JwtUtil) - no
 * separate Auth Service exists anymore.
 * <p>
 * {@code com.platform.notification} is a sibling package, not nested under
 * {@code com.platform.analytics} - scanBasePackages widens component
 * scanning to cover it. Repository/entity scanning for that package is
 * handled separately by config.JpaRepositoryConfig, NOT here - putting
 * @EnableJpaRepositories/@EntityScan directly on this class would also
 * apply them inside @WebMvcTest slices (which don't set up a datasource at
 * all), breaking every controller-slice test.
 */
@SpringBootApplication(scanBasePackages = {"com.platform.analytics", "com.platform.notification"})
@EnableFeignClients(basePackages = "com.platform.analytics")
@EnableScheduling
@EnableAsync
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
