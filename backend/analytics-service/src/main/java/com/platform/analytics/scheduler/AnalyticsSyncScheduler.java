package com.platform.analytics.scheduler;

import com.platform.analytics.service.AnalyticsSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Automatically synchronizes analytics for every active connected account
 * on an hourly cadence (configurable via {@code analytics.sync.cron}).
 * Disabled in tests via {@code analytics.sync.enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "analytics.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsSyncScheduler {

    private final AnalyticsSyncService analyticsSyncService;

    @Scheduled(cron = "${analytics.sync.cron:0 0 * * * *}")
    public void syncAllAccounts() {
        log.info("Scheduled analytics synchronization triggered");
        try {
            analyticsSyncService.syncAllActiveAccounts();
        } catch (Exception ex) {
            log.error("Scheduled synchronization run failed: {}", ex.getMessage(), ex);
        }
    }
}
