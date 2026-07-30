package com.platform.analytics.service.impl;

import com.platform.analytics.repository.AnalyticsRepository;
import com.platform.analytics.repository.SocialAccountRepository;
import com.platform.analytics.service.UserDataCleanupService;
import com.platform.notification.service.NotificationService;
import com.platform.notification.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataCleanupServiceImpl implements UserDataCleanupService {

    private final AnalyticsRepository analyticsRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final NotificationService notificationService;
    private final ReportService reportService;

    @Override
    @Transactional
    public void deleteAllDataForUser(UUID userId) {
        // Analytics rows hold a NOT NULL FK to social_accounts - must go
        // first, same ordering as the single-account disconnect flow.
        int analyticsDeleted = analyticsRepository.deleteAllByUserId(userId);
        socialAccountRepository.deleteAllByUserId(userId);
        notificationService.deleteAllForUser(userId);
        reportService.deleteAllForUser(userId);
        log.info("Deleted all data for user {}: {} analytics row(s), plus their accounts/notifications/reports",
                userId, analyticsDeleted);
    }
}
