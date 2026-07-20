package com.platform.notification;

import com.platform.notification.client.AnalyticsServiceClient;
import com.platform.notification.client.AuthServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the full Spring application context loads successfully with all
 * beans wired correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceApplicationTests {

    // These Feign clients would attempt a real connection during context
    // initialization checks in some environments; mocking them keeps this
    // test focused purely on context wiring.
    @MockBean
    private AuthServiceClient authServiceClient;

    @MockBean
    private AnalyticsServiceClient analyticsServiceClient;

    @Test
    void contextLoads() {
    }
}
