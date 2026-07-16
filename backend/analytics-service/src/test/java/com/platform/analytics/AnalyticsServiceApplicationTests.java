package com.platform.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import com.platform.analytics.client.AuthServiceClient;

/**
 * Verifies the full Spring application context loads successfully with all
 * beans wired correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsServiceApplicationTests {

    // The Feign client would attempt a real connection during context
    // initialization checks in some environments; mocking it keeps this
    // test focused purely on context wiring.
    @MockBean
    private AuthServiceClient authServiceClient;

    @Test
    void contextLoads() {
    }
}
