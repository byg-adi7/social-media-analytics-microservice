package com.platform.analytics.service;

import com.platform.analytics.entity.SocialAccount;

/**
 * Responsible for pulling fresh metrics from a social platform (via
 * {@link com.platform.analytics.client.SocialMediaClient}) and persisting
 * them as an {@link com.platform.analytics.entity.Analytics} record. Used
 * both by the on-demand sync endpoint and the hourly scheduler.
 */
public interface AnalyticsSyncService {

    /**
     * Syncs today's analytics snapshot for a single account.
     */
    void syncAccount(SocialAccount account);

    /**
     * Syncs today's analytics snapshot for every active account across all
     * users. Invoked by the hourly scheduler.
     */
    void syncAllActiveAccounts();
}
