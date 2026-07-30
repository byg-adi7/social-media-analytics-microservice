package com.platform.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the full Spring application context loads successfully with all
 * beans wired correctly - including the merged-in com.platform.notification
 * package, which is a sibling package the app's scanBasePackages must list
 * explicitly (see AnalyticsServiceApplication).
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
